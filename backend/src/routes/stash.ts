import { Hono } from 'hono';
import type { Env } from '../types';
import { requireAuth, type AuthVariables } from '../middleware/auth';
import { notifyPartner } from '../lib/notify';
import { fetchLinkPreview, isSupportedPreviewUrl, parseHttpUrl } from '../lib/linkPreview';

export const stashRoutes = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

stashRoutes.use('*', requireAuth);

const TYPES = ['movie', 'link', 'place', 'note', 'todo'] as const;
type StashType = (typeof TYPES)[number];

const STATUSES = ['saved', 'done'] as const;
type StashStatus = (typeof STATUSES)[number];

const STATUS_FILTERS = ['saved', 'done', 'everything'] as const;

function isType(value: unknown): value is StashType {
  return typeof value === 'string' && (TYPES as readonly string[]).includes(value);
}

function isStatus(value: unknown): value is StashStatus {
  return typeof value === 'string' && (STATUSES as readonly string[]).includes(value);
}

function isStatusFilter(value: unknown): value is (typeof STATUS_FILTERS)[number] {
  return typeof value === 'string' && (STATUS_FILTERS as readonly string[]).includes(value);
}

type StashRow = {
  id: string;
  type: StashType;
  author: string;
  title: string;
  body: string | null;
  url: string | null;
  tags_json: string;
  status: StashStatus;
  created_at: string;
  updated_at: string;
};

function toItemJson(row: StashRow) {
  return {
    id: row.id,
    type: row.type,
    author: row.author,
    title: row.title,
    body: row.body,
    url: row.url,
    tags: JSON.parse(row.tags_json) as string[],
    status: row.status,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

/** Only movies and places keep a link; returns undefined for "not a valid link". */
function normalizeUrl(value: unknown): string | null | undefined {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value !== 'string') return undefined;
  return parseHttpUrl(value)?.toString();
}

const URL_TYPES: readonly StashType[] = ['movie', 'place'];

function parseNonNegativeInt(value: string | undefined): number | null | undefined {
  if (value === undefined) return undefined;
  return /^\d+$/.test(value) ? Number(value) : null;
}

stashRoutes.get('/', async (c) => {
  const statusParam = c.req.query('status') ?? 'saved';
  if (!isStatusFilter(statusParam)) {
    return c.json({ error: 'status must be saved, done, or everything' }, 400);
  }
  const typeParam = c.req.query('type');
  if (typeParam !== undefined && !isType(typeParam)) {
    return c.json({ error: 'invalid type' }, 400);
  }
  const tagParam = c.req.query('tag');
  const limit = parseNonNegativeInt(c.req.query('limit'));
  const offset = parseNonNegativeInt(c.req.query('offset'));
  if (limit === null || limit === 0 || offset === null) {
    return c.json({ error: 'limit must be a positive integer and offset a non-negative integer' }, 400);
  }

  const conditions: string[] = [];
  const bindings: (string | number)[] = [];
  if (statusParam !== 'everything') {
    conditions.push('status = ?');
    bindings.push(statusParam);
  }
  if (typeParam) {
    conditions.push('type = ?');
    bindings.push(typeParam);
  }
  if (tagParam) {
    conditions.push('EXISTS (SELECT 1 FROM json_each(stash_items.tags_json) WHERE value = ?)');
    bindings.push(tagParam);
  }
  const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';

  // Done items sink below pending ones server-side so paging stays consistent with what the
  // app shows; id breaks created_at ties (second resolution) so pages never overlap or skip.
  let sql = `SELECT * FROM stash_items ${where} ORDER BY status = 'done', created_at DESC, id`;
  if (limit !== undefined) {
    sql += ' LIMIT ? OFFSET ?';
    bindings.push(limit, offset ?? 0);
  }

  const { results } = await c.env.DB.prepare(sql)
    .bind(...bindings)
    .all<StashRow>();
  return c.json(results.map(toItemJson));
});

/** Every (type, tag) pair in use, most recently used first - lets the app build tag
 * suggestions and filters without downloading every item. */
stashRoutes.get('/tags', async (c) => {
  const { results } = await c.env.DB.prepare(
    `SELECT s.type AS type, j.value AS tag, MAX(s.created_at) AS last_used
     FROM stash_items s, json_each(s.tags_json) j
     GROUP BY s.type, j.value
     ORDER BY last_used DESC`,
  ).all<{ type: StashType; tag: string }>();
  return c.json(results.map((r) => ({ type: r.type, tag: r.tag })));
});

/** Text-only preview (title + description) for an IMDb or Google Maps link - see lib/linkPreview.ts. */
stashRoutes.get('/preview', async (c) => {
  const url = parseHttpUrl(c.req.query('url') ?? '');
  if (!url) return c.json({ error: 'url must be an http(s) link' }, 400);
  if (!isSupportedPreviewUrl(url)) return c.json({ error: 'only imdb and google maps links can be fetched' }, 400);
  const preview = await fetchLinkPreview(url);
  if (!preview) return c.json({ error: "couldn't read that link - fill it in by hand" }, 422);
  return c.json(preview);
});

stashRoutes.post('/', async (c) => {
  const body = await c.req.json().catch(() => null);
  const { type, title, body: itemBody, tags, url: rawUrl } = body ?? {};

  if (!isType(type) || typeof title !== 'string' || title.trim().length === 0) {
    return c.json({ error: 'type (movie/link/place/note/todo) and title are required' }, 400);
  }
  if (tags !== undefined && (!Array.isArray(tags) || !tags.every((t) => typeof t === 'string'))) {
    return c.json({ error: 'tags must be an array of strings' }, 400);
  }

  const url = normalizeUrl(rawUrl);
  if (url === undefined) return c.json({ error: 'url must be an http(s) link' }, 400);

  const id = crypto.randomUUID();
  await c.env.DB.prepare(
    `INSERT INTO stash_items (id, type, author, title, body, url, tags_json) VALUES (?, ?, ?, ?, ?, ?, ?)`,
  )
    .bind(
      id,
      type,
      c.var.username,
      title.trim(),
      typeof itemBody === 'string' ? itemBody : null,
      URL_TYPES.includes(type) ? url : null,
      JSON.stringify(tags ?? []),
    )
    .run();

  const row = await c.env.DB.prepare('SELECT * FROM stash_items WHERE id = ?').bind(id).first<StashRow>();

  c.executionCtx.waitUntil(
    notifyPartner(c.env, c.var.username, 'New in Stash', `${c.var.username} added '${title.trim()}' to Stash`, {
      route: 'stash',
    }),
  );

  return c.json(toItemJson(row!), 201);
});

stashRoutes.patch('/:id', async (c) => {
  const id = c.req.param('id');
  const existing = await c.env.DB.prepare('SELECT * FROM stash_items WHERE id = ?').bind(id).first<StashRow>();
  if (!existing) return c.json({ error: 'item not found' }, 404);

  const body = await c.req.json().catch(() => null);
  if (!body) return c.json({ error: 'invalid body' }, 400);

  const type = body.type !== undefined ? body.type : existing.type;
  if (!isType(type)) return c.json({ error: 'invalid type' }, 400);

  const title = body.title !== undefined ? String(body.title).trim() : existing.title;
  if (title.length === 0) return c.json({ error: 'title cannot be empty' }, 400);

  const status = body.status !== undefined ? body.status : existing.status;
  if (!isStatus(status)) return c.json({ error: 'invalid status' }, 400);

  const itemBody = body.body !== undefined ? body.body : existing.body;
  const url = body.url !== undefined ? normalizeUrl(body.url) : existing.url;
  if (url === undefined) return c.json({ error: 'url must be an http(s) link' }, 400);
  let tagsJson = existing.tags_json;
  if (body.tags !== undefined) {
    if (!Array.isArray(body.tags) || !body.tags.every((t: unknown) => typeof t === 'string')) {
      return c.json({ error: 'tags must be an array of strings' }, 400);
    }
    tagsJson = JSON.stringify(body.tags);
  }

  await c.env.DB.prepare(
    `UPDATE stash_items SET type = ?, title = ?, body = ?, url = ?, tags_json = ?, status = ?, updated_at = datetime('now')
     WHERE id = ?`,
  )
    .bind(type, title, typeof itemBody === 'string' ? itemBody : null, URL_TYPES.includes(type) ? url : null, tagsJson, status, id)
    .run();

  const row = await c.env.DB.prepare('SELECT * FROM stash_items WHERE id = ?').bind(id).first<StashRow>();
  return c.json(toItemJson(row!));
});

stashRoutes.post('/:id/toggle', async (c) => {
  const id = c.req.param('id');
  const existing = await c.env.DB.prepare('SELECT * FROM stash_items WHERE id = ?').bind(id).first<StashRow>();
  if (!existing) return c.json({ error: 'item not found' }, 404);

  const newStatus: StashStatus = existing.status === 'saved' ? 'done' : 'saved';
  await c.env.DB.prepare("UPDATE stash_items SET status = ?, updated_at = datetime('now') WHERE id = ?")
    .bind(newStatus, id)
    .run();

  const row = await c.env.DB.prepare('SELECT * FROM stash_items WHERE id = ?').bind(id).first<StashRow>();
  return c.json(toItemJson(row!));
});

stashRoutes.delete('/:id', async (c) => {
  const id = c.req.param('id');
  const result = await c.env.DB.prepare('DELETE FROM stash_items WHERE id = ?').bind(id).run();
  if (result.meta.changes === 0) return c.json({ error: 'item not found' }, 404);
  return c.json({ ok: true });
});
