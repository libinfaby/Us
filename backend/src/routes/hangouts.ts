import { Hono } from 'hono';
import type { Env } from '../types';
import { requireAuth, type AuthVariables } from '../middleware/auth';

export const hangoutsRoutes = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

hangoutsRoutes.use('*', requireAuth);

type HangoutRow = { id: string; name: string; start_date: string | null; end_date: string | null; created_at: string };
type MemoryRow = { id: string; hangout_id: string; author: string; text: string; created_at: string };

function toMemoryJson(row: MemoryRow) {
  return { id: row.id, hangoutId: row.hangout_id, author: row.author, text: row.text, createdAt: row.created_at };
}

function toHangoutJson(row: HangoutRow, totalCents: number, memories: MemoryRow[]) {
  return {
    id: row.id,
    name: row.name,
    startDate: row.start_date,
    endDate: row.end_date,
    createdAt: row.created_at,
    totalCents,
    memories: memories.map(toMemoryJson),
  };
}

async function loadHangout(env: Env, id: string) {
  const row = await env.DB.prepare('SELECT * FROM hangouts WHERE id = ?').bind(id).first<HangoutRow>();
  if (!row) return null;
  const totalRow = await env.DB.prepare('SELECT SUM(amount_cents) as total FROM expenses WHERE hangout_id = ?')
    .bind(id)
    .first<{ total: number | null }>();
  const { results: memories } = await env.DB.prepare(
    'SELECT * FROM hangout_memories WHERE hangout_id = ? ORDER BY created_at ASC',
  )
    .bind(id)
    .all<MemoryRow>();
  return toHangoutJson(row, totalRow?.total ?? 0, memories);
}

hangoutsRoutes.get('/', async (c) => {
  const [{ results: hangoutRows }, { results: sumRows }, { results: memoryRows }] = await Promise.all([
    c.env.DB.prepare('SELECT * FROM hangouts ORDER BY created_at DESC').all<HangoutRow>(),
    c.env.DB.prepare(
      'SELECT hangout_id, SUM(amount_cents) as total FROM expenses WHERE hangout_id IS NOT NULL GROUP BY hangout_id',
    ).all<{ hangout_id: string; total: number }>(),
    c.env.DB.prepare('SELECT * FROM hangout_memories ORDER BY created_at ASC').all<MemoryRow>(),
  ]);

  const totals = new Map(sumRows.map((r) => [r.hangout_id, r.total]));
  const memoriesByHangout = new Map<string, MemoryRow[]>();
  for (const m of memoryRows) {
    const list = memoriesByHangout.get(m.hangout_id) ?? [];
    list.push(m);
    memoriesByHangout.set(m.hangout_id, list);
  }

  return c.json(
    hangoutRows.map((row) => toHangoutJson(row, totals.get(row.id) ?? 0, memoriesByHangout.get(row.id) ?? [])),
  );
});

hangoutsRoutes.post('/', async (c) => {
  const body = await c.req.json().catch(() => null);
  const { name, startDate, endDate } = body ?? {};
  if (typeof name !== 'string' || name.trim().length === 0) {
    return c.json({ error: 'name is required' }, 400);
  }

  const id = crypto.randomUUID();
  await c.env.DB.prepare('INSERT INTO hangouts (id, name, start_date, end_date) VALUES (?, ?, ?, ?)')
    .bind(id, name.trim(), typeof startDate === 'string' ? startDate : null, typeof endDate === 'string' ? endDate : null)
    .run();
  return c.json(await loadHangout(c.env, id), 201);
});

hangoutsRoutes.patch('/:id', async (c) => {
  const id = c.req.param('id');
  const existing = await c.env.DB.prepare('SELECT * FROM hangouts WHERE id = ?').bind(id).first<HangoutRow>();
  if (!existing) return c.json({ error: 'hangout not found' }, 404);

  const body = await c.req.json().catch(() => null);
  if (!body) return c.json({ error: 'invalid body' }, 400);

  const name = body.name !== undefined ? String(body.name).trim() : existing.name;
  if (name.length === 0) return c.json({ error: 'name cannot be empty' }, 400);
  const startDate = body.startDate !== undefined ? (typeof body.startDate === 'string' ? body.startDate : null) : existing.start_date;
  const endDate = body.endDate !== undefined ? (typeof body.endDate === 'string' ? body.endDate : null) : existing.end_date;

  await c.env.DB.prepare('UPDATE hangouts SET name = ?, start_date = ?, end_date = ? WHERE id = ?')
    .bind(name, startDate, endDate, id)
    .run();
  return c.json(await loadHangout(c.env, id));
});

hangoutsRoutes.delete('/:id', async (c) => {
  const id = c.req.param('id');
  const existing = await c.env.DB.prepare('SELECT * FROM hangouts WHERE id = ?').bind(id).first<HangoutRow>();
  if (!existing) return c.json({ error: 'hangout not found' }, 404);

  await c.env.DB.batch([
    c.env.DB.prepare('UPDATE expenses SET hangout_id = NULL WHERE hangout_id = ?').bind(id),
    c.env.DB.prepare('DELETE FROM hangout_memories WHERE hangout_id = ?').bind(id),
    c.env.DB.prepare('DELETE FROM hangouts WHERE id = ?').bind(id),
  ]);
  return c.body(null, 204);
});

hangoutsRoutes.post('/:id/memories', async (c) => {
  const hangoutId = c.req.param('id');
  const hangout = await c.env.DB.prepare('SELECT * FROM hangouts WHERE id = ?').bind(hangoutId).first<HangoutRow>();
  if (!hangout) return c.json({ error: 'hangout not found' }, 404);

  const body = await c.req.json().catch(() => null);
  const { text } = body ?? {};
  if (typeof text !== 'string' || text.trim().length === 0) {
    return c.json({ error: 'text is required' }, 400);
  }

  const id = crypto.randomUUID();
  await c.env.DB.prepare('INSERT INTO hangout_memories (id, hangout_id, author, text) VALUES (?, ?, ?, ?)')
    .bind(id, hangoutId, c.var.username, text.trim())
    .run();
  const row = await c.env.DB.prepare('SELECT * FROM hangout_memories WHERE id = ?').bind(id).first<MemoryRow>();
  return c.json(toMemoryJson(row!), 201);
});

hangoutsRoutes.patch('/:id/memories/:memoryId', async (c) => {
  const hangoutId = c.req.param('id');
  const memoryId = c.req.param('memoryId');
  const existing = await c.env.DB.prepare('SELECT * FROM hangout_memories WHERE id = ? AND hangout_id = ?')
    .bind(memoryId, hangoutId)
    .first<MemoryRow>();
  if (!existing) return c.json({ error: 'memory not found' }, 404);

  const body = await c.req.json().catch(() => null);
  const { text } = body ?? {};
  if (typeof text !== 'string' || text.trim().length === 0) {
    return c.json({ error: 'text is required' }, 400);
  }

  await c.env.DB.prepare('UPDATE hangout_memories SET text = ? WHERE id = ?').bind(text.trim(), memoryId).run();
  const row = await c.env.DB.prepare('SELECT * FROM hangout_memories WHERE id = ?').bind(memoryId).first<MemoryRow>();
  return c.json(toMemoryJson(row!));
});

hangoutsRoutes.delete('/:id/memories/:memoryId', async (c) => {
  const hangoutId = c.req.param('id');
  const memoryId = c.req.param('memoryId');
  const existing = await c.env.DB.prepare('SELECT * FROM hangout_memories WHERE id = ? AND hangout_id = ?')
    .bind(memoryId, hangoutId)
    .first<MemoryRow>();
  if (!existing) return c.json({ error: 'memory not found' }, 404);
  if (existing.author !== c.var.username) {
    return c.json({ error: 'only the author can delete this memory' }, 403);
  }

  await c.env.DB.prepare('DELETE FROM hangout_memories WHERE id = ?').bind(memoryId).run();
  return c.body(null, 204);
});
