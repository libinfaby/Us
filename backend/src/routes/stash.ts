import { Hono } from 'hono';
import type { Env } from '../types';
import { requireAuth, type AuthVariables } from '../middleware/auth';
import { notifyPartner } from '../lib/notify';

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
    tags: JSON.parse(row.tags_json) as string[],
    status: row.status,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
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

  const conditions: string[] = [];
  const bindings: string[] = [];
  if (statusParam !== 'everything') {
    conditions.push('status = ?');
    bindings.push(statusParam);
  }
  if (typeParam) {
    conditions.push('type = ?');
    bindings.push(typeParam);
  }
  const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';

  const { results } = await c.env.DB.prepare(`SELECT * FROM stash_items ${where} ORDER BY created_at DESC`)
    .bind(...bindings)
    .all<StashRow>();
  return c.json(results.map(toItemJson));
});

stashRoutes.post('/', async (c) => {
  const body = await c.req.json().catch(() => null);
  const { type, title, body: itemBody, tags } = body ?? {};

  if (!isType(type) || typeof title !== 'string' || title.trim().length === 0) {
    return c.json({ error: 'type (movie/link/place/note/todo) and title are required' }, 400);
  }
  if (tags !== undefined && (!Array.isArray(tags) || !tags.every((t) => typeof t === 'string'))) {
    return c.json({ error: 'tags must be an array of strings' }, 400);
  }

  const id = crypto.randomUUID();
  await c.env.DB.prepare(
    `INSERT INTO stash_items (id, type, author, title, body, tags_json) VALUES (?, ?, ?, ?, ?, ?)`,
  )
    .bind(id, type, c.var.username, title.trim(), typeof itemBody === 'string' ? itemBody : null, JSON.stringify(tags ?? []))
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
  let tagsJson = existing.tags_json;
  if (body.tags !== undefined) {
    if (!Array.isArray(body.tags) || !body.tags.every((t: unknown) => typeof t === 'string')) {
      return c.json({ error: 'tags must be an array of strings' }, 400);
    }
    tagsJson = JSON.stringify(body.tags);
  }

  await c.env.DB.prepare(
    `UPDATE stash_items SET type = ?, title = ?, body = ?, tags_json = ?, status = ?, updated_at = datetime('now')
     WHERE id = ?`,
  )
    .bind(type, title, typeof itemBody === 'string' ? itemBody : null, tagsJson, status, id)
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
