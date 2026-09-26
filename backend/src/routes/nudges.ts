import { Hono } from 'hono';
import type { Env } from '../types';
import { requireAuth, type AuthVariables } from '../middleware/auth';
import { notifyPartner } from '../lib/notify';
import { todayIst } from '../lib/dates';

export const nudgesRoutes = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

nudgesRoutes.use('*', requireAuth);

const MAX_MESSAGE_LENGTH = 80;
const COOLDOWN_SECONDS = 10;

type NudgeRow = { id: string; sender: string; message: string; created_at: string };

function toNudgeJson(row: NudgeRow) {
  return { id: row.id, sender: row.sender, message: row.message, createdAt: row.created_at };
}

/** The reason a (trimmed) message can't be saved, or null when it's fine. */
function messageError(message: string): string | null {
  if (message.length === 0) return 'message cannot be empty';
  if (message.length > MAX_MESSAGE_LENGTH) return `message must be at most ${MAX_MESSAGE_LENGTH} characters`;
  return null;
}

nudgesRoutes.post('/', async (c) => {
  const body = await c.req.json().catch(() => ({}));
  if (typeof body?.message !== 'string') return c.json({ error: 'message must be a string' }, 400);
  const message = body.message.trim();
  const error = messageError(message);
  if (error) return c.json({ error }, 400);

  const recent = await c.env.DB.prepare(
    `SELECT 1 FROM nudges WHERE sender = ? AND created_at > datetime('now', ?) LIMIT 1`,
  )
    .bind(c.var.username, `-${COOLDOWN_SECONDS} seconds`)
    .first();
  if (recent) return c.json({ error: 'slow down' }, 429);

  const id = crypto.randomUUID();
  await c.env.DB.prepare('INSERT INTO nudges (id, sender, message) VALUES (?, ?, ?)')
    .bind(id, c.var.username, message)
    .run();
  const row = await c.env.DB.prepare('SELECT * FROM nudges WHERE id = ?').bind(id).first<NudgeRow>();

  c.executionCtx.waitUntil(notifyPartner(c.env, c.var.username, c.var.username, message, { route: 'home' }));

  return c.json(toNudgeJson(row!), 201);
});

/** The newest nudge the *other* person sent me - wrapped, so "never nudged" is `{ nudge: null }`
 * rather than a bare JSON null the app's decoder would choke on. */
nudgesRoutes.get('/latest', async (c) => {
  const row = await c.env.DB.prepare(
    'SELECT * FROM nudges WHERE sender != ? ORDER BY created_at DESC, id DESC LIMIT 1',
  )
    .bind(c.var.username)
    .first<NudgeRow>();
  return c.json({ nudge: row ? toNudgeJson(row) : null });
});

/** Every note either of us sent today (IST), oldest first. */
nudgesRoutes.get('/today', async (c) => {
  // created_at is UTC, and IST midnight is 5h30m earlier in UTC.
  const { results } = await c.env.DB.prepare(
    `SELECT * FROM nudges WHERE created_at >= datetime(?, '-330 minutes') ORDER BY created_at ASC, id ASC`,
  )
    .bind(`${todayIst()} 00:00:00`)
    .all<NudgeRow>();
  return c.json(results.map(toNudgeJson));
});

/** Only the sender can edit their note. No second push - the partner already got one. */
nudgesRoutes.patch('/:id', async (c) => {
  const id = c.req.param('id');
  const existing = await c.env.DB.prepare('SELECT * FROM nudges WHERE id = ?').bind(id).first<NudgeRow>();
  if (!existing) return c.json({ error: 'note not found' }, 404);
  if (existing.sender !== c.var.username) return c.json({ error: 'you can only edit your own notes' }, 403);

  const body = await c.req.json().catch(() => ({}));
  if (typeof body?.message !== 'string') return c.json({ error: 'message must be a string' }, 400);
  const message = body.message.trim();
  const error = messageError(message);
  if (error) return c.json({ error }, 400);

  await c.env.DB.prepare('UPDATE nudges SET message = ? WHERE id = ?').bind(message, id).run();
  return c.json(toNudgeJson({ ...existing, message }));
});

nudgesRoutes.delete('/:id', async (c) => {
  const id = c.req.param('id');
  const existing = await c.env.DB.prepare('SELECT sender FROM nudges WHERE id = ?').bind(id).first<{ sender: string }>();
  if (!existing) return c.json({ error: 'note not found' }, 404);
  if (existing.sender !== c.var.username) return c.json({ error: 'you can only delete your own notes' }, 403);
  await c.env.DB.prepare('DELETE FROM nudges WHERE id = ?').bind(id).run();
  return c.json({ ok: true });
});
