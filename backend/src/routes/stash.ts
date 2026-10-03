import { Hono } from 'hono';
import type { Env } from '../types';
import { requireAuth, type AuthVariables } from '../middleware/auth';
import { notifyPartner } from '../lib/notify';
import { bookPreview, fetchLinkPreview, filmPreview, isSupportedPreviewUrl, parseHttpUrl, searchBooks, searchMovies } from '../lib/linkPreview';

export const stashRoutes = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

stashRoutes.use('*', requireAuth);

const TYPES = ['movie', 'book', 'activity', 'link', 'place', 'note', 'todo'] as const;
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
  hangout_id: string | null;
  place_id: string | null;
  tags_json: string;
  status: StashStatus;
  created_at: string;
  updated_at: string;
};

/** A row from [ITEM_SELECT]: the item plus its place's title and, for a place, how many
 * activities are tied to it. */
type StashItemRow = StashRow & { place_title: string | null; activity_count: number };

/** Selects items as `s`, joined with what toItemJson needs from their linked place. */
const ITEM_SELECT = `SELECT s.*, p.title AS place_title,
    (SELECT COUNT(*) FROM stash_items a WHERE a.place_id = s.id AND a.type = 'activity') AS activity_count
  FROM stash_items s LEFT JOIN stash_items p ON p.id = s.place_id`;

function toItemJson(row: StashItemRow) {
  return {
    id: row.id,
    type: row.type,
    author: row.author,
    title: row.title,
    body: row.body,
    url: row.url,
    hangoutId: row.hangout_id,
    placeId: row.place_id,
    placeTitle: row.place_title,
    activityCount: row.activity_count,
    tags: JSON.parse(row.tags_json) as string[],
    status: row.status,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

/** Only movies, books and places keep a link; returns undefined for "not a valid link". */
function normalizeUrl(value: unknown): string | null | undefined {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value !== 'string') return undefined;
  return parseHttpUrl(value)?.toString();
}

const URL_TYPES: readonly StashType[] = ['movie', 'book', 'place'];

/** A hangout id from a request body: null for none, undefined for an id that doesn't exist. */
async function resolveHangoutId(env: Env, value: unknown): Promise<string | null | undefined> {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value !== 'string') return undefined;
  const row = await env.DB.prepare('SELECT id FROM hangouts WHERE id = ?').bind(value).first();
  return row ? value : undefined;
}

/** A place id for an activity: null for none, undefined for an id that isn't a saved place. */
async function resolvePlaceId(env: Env, value: unknown): Promise<string | null | undefined> {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value !== 'string') return undefined;
  const row = await env.DB.prepare("SELECT id FROM stash_items WHERE id = ? AND type = 'place'").bind(value).first();
  return row ? value : undefined;
}

async function fetchItem(env: Env, id: string): Promise<StashItemRow | null> {
  return env.DB.prepare(`${ITEM_SELECT} WHERE s.id = ?`).bind(id).first<StashItemRow>();
}

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
  const placeParam = c.req.query('placeId');
  const limit = parseNonNegativeInt(c.req.query('limit'));
  const offset = parseNonNegativeInt(c.req.query('offset'));
  if (limit === null || limit === 0 || offset === null) {
    return c.json({ error: 'limit must be a positive integer and offset a non-negative integer' }, 400);
  }

  const conditions: string[] = [];
  const bindings: (string | number)[] = [];
  if (statusParam !== 'everything') {
    conditions.push('s.status = ?');
    bindings.push(statusParam);
  }
  if (typeParam) {
    conditions.push('s.type = ?');
    bindings.push(typeParam);
  }
  if (tagParam) {
    conditions.push('EXISTS (SELECT 1 FROM json_each(s.tags_json) WHERE value = ?)');
    bindings.push(tagParam);
  }
  if (placeParam) {
    conditions.push('s.place_id = ?');
    bindings.push(placeParam);
  }
  const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';

  // Done items sink below pending ones server-side so paging stays consistent with what the
  // app shows; id breaks created_at ties (second resolution) so pages never overlap or skip.
  let sql = `${ITEM_SELECT} ${where} ORDER BY s.status = 'done', s.created_at DESC, s.id`;
  if (limit !== undefined) {
    sql += ' LIMIT ? OFFSET ?';
    bindings.push(limit, offset ?? 0);
  }

  const { results } = await c.env.DB.prepare(sql)
    .bind(...bindings)
    .all<StashItemRow>();
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

/** Text-only preview (title + description) for an IMDb, Google Books or Google Maps link - see lib/linkPreview.ts. */
stashRoutes.get('/preview', async (c) => {
  const url = parseHttpUrl(c.req.query('url') ?? '');
  if (!url) return c.json({ error: 'url must be an http(s) link' }, 400);
  if (!isSupportedPreviewUrl(url)) return c.json({ error: 'only imdb, google books and google maps links can be fetched' }, 400);
  const preview = await fetchLinkPreview(url, c.env.OMDB_API_KEY, c.env.GOOGLE_BOOKS_API_KEY);
  if (!preview) return c.json({ error: "couldn't read that link - fill it in by hand" }, 422);
  return c.json(preview);
});

/** Films matching a typed name, for the movie form's "find" button. */
stashRoutes.get('/movie-search', async (c) => {
  const query = (c.req.query('q') ?? '').trim().slice(0, 100);
  if (query.length === 0) return c.json({ error: 'type a movie name first' }, 400);
  const results = await searchMovies(query);
  if (!results) return c.json({ error: "couldn't search right now - try again" }, 502);
  return c.json(results);
});

/** Title, plot, genres and IMDb link for a film picked from /movie-search, by its Wikidata id. */
stashRoutes.get('/movie-preview', async (c) => {
  const preview = await filmPreview(c.req.query('id') ?? '', c.env.OMDB_API_KEY);
  if (!preview) return c.json({ error: "couldn't load that movie - fill it in by hand" }, 422);
  return c.json(preview);
});

/** Books matching a typed name, for the book form's "find" button. */
stashRoutes.get('/book-search', async (c) => {
  const query = (c.req.query('q') ?? '').trim().slice(0, 100);
  if (query.length === 0) return c.json({ error: 'type a book name first' }, 400);
  const results = await searchBooks(query, c.env.GOOGLE_BOOKS_API_KEY);
  if (!results) return c.json({ error: "couldn't search right now - try again" }, 502);
  return c.json(results);
});

/** Title, blurb, genres and Google Books link for a book picked from /book-search, by its volume id. */
stashRoutes.get('/book-preview', async (c) => {
  const preview = await bookPreview(c.req.query('id') ?? '', c.env.GOOGLE_BOOKS_API_KEY);
  if (!preview) return c.json({ error: "couldn't load that book - fill it in by hand" }, 422);
  return c.json(preview);
});

stashRoutes.post('/', async (c) => {
  const body = await c.req.json().catch(() => null);
  const { type, title, body: itemBody, tags, url: rawUrl, hangoutId: rawHangoutId, placeId: rawPlaceId } = body ?? {};

  if (!isType(type) || typeof title !== 'string' || title.trim().length === 0) {
    return c.json({ error: 'type (movie/book/activity/link/place/note/todo) and title are required' }, 400);
  }
  if (tags !== undefined && (!Array.isArray(tags) || !tags.every((t) => typeof t === 'string'))) {
    return c.json({ error: 'tags must be an array of strings' }, 400);
  }

  const url = normalizeUrl(rawUrl);
  if (url === undefined) return c.json({ error: 'url must be an http(s) link' }, 400);
  const hangoutId = await resolveHangoutId(c.env, rawHangoutId);
  if (hangoutId === undefined) return c.json({ error: 'unknown hangoutId' }, 400);
  const placeId = await resolvePlaceId(c.env, rawPlaceId);
  if (placeId === undefined) return c.json({ error: 'placeId must be a saved place' }, 400);

  const id = crypto.randomUUID();
  await c.env.DB.prepare(
    `INSERT INTO stash_items (id, type, author, title, body, url, hangout_id, place_id, tags_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  )
    .bind(
      id,
      type,
      c.var.username,
      title.trim(),
      typeof itemBody === 'string' ? itemBody : null,
      URL_TYPES.includes(type) ? url : null,
      hangoutId,
      type === 'activity' ? placeId : null,
      JSON.stringify(tags ?? []),
    )
    .run();

  const row = await fetchItem(c.env, id);

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
  const hangoutId = body.hangoutId !== undefined ? await resolveHangoutId(c.env, body.hangoutId) : existing.hangout_id;
  if (hangoutId === undefined) return c.json({ error: 'unknown hangoutId' }, 400);
  const placeId = body.placeId !== undefined ? await resolvePlaceId(c.env, body.placeId) : existing.place_id;
  if (placeId === undefined) return c.json({ error: 'placeId must be a saved place' }, 400);
  let tagsJson = existing.tags_json;
  if (body.tags !== undefined) {
    if (!Array.isArray(body.tags) || !body.tags.every((t: unknown) => typeof t === 'string')) {
      return c.json({ error: 'tags must be an array of strings' }, 400);
    }
    tagsJson = JSON.stringify(body.tags);
  }

  await c.env.DB.prepare(
    `UPDATE stash_items SET type = ?, title = ?, body = ?, url = ?, hangout_id = ?, place_id = ?, tags_json = ?, status = ?, updated_at = datetime('now')
     WHERE id = ?`,
  )
    .bind(
      type,
      title,
      typeof itemBody === 'string' ? itemBody : null,
      URL_TYPES.includes(type) ? url : null,
      hangoutId,
      type === 'activity' ? placeId : null,
      tagsJson,
      status,
      id,
    )
    .run();
  // A place turned into something else no longer anchors activities.
  if (existing.type === 'place' && type !== 'place') {
    await c.env.DB.prepare('UPDATE stash_items SET place_id = NULL WHERE place_id = ?').bind(id).run();
  }

  const row = await fetchItem(c.env, id);
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

  const row = await fetchItem(c.env, id);
  return c.json(toItemJson(row!));
});

stashRoutes.delete('/:id', async (c) => {
  const id = c.req.param('id');
  // Deleting a place unlinks its activities rather than deleting them.
  const [, result] = await c.env.DB.batch([
    c.env.DB.prepare('UPDATE stash_items SET place_id = NULL WHERE place_id = ?').bind(id),
    c.env.DB.prepare('DELETE FROM stash_items WHERE id = ?').bind(id),
  ]);
  if (result.meta.changes === 0) return c.json({ error: 'item not found' }, 404);
  return c.json({ ok: true });
});
