import { Hono } from 'hono';
import type { Env } from '../types';
import { requireAuth, type AuthVariables } from '../middleware/auth';

export const hangoutsRoutes = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

hangoutsRoutes.use('*', requireAuth);

type HangoutRow = { id: string; name: string; start_date: string | null; end_date: string | null; created_at: string };
type MemoryRow = { id: string; hangout_id: string; author: string; text: string; created_at: string };
type LinkedStashRow = { id: string; hangout_id: string; type: string; title: string; status: string };
type LinkedExpenseRow = {
  id: string;
  hangout_id: string;
  title: string;
  amount_cents: number;
  expense_date: string;
  paid_by: string;
  status: string;
};

// What a hangout card lists: just enough of each stash item / expense to show a row.
const LINKED_STASH_SQL = 'SELECT id, hangout_id, type, title, status FROM stash_items';
const LINKED_EXPENSES_SQL = 'SELECT id, hangout_id, title, amount_cents, expense_date, paid_by, status FROM expenses';

type Linked = { stashItems: LinkedStashRow[]; expenses: LinkedExpenseRow[] };

function toMemoryJson(row: MemoryRow) {
  return { id: row.id, hangoutId: row.hangout_id, author: row.author, text: row.text, createdAt: row.created_at };
}

function toHangoutJson(row: HangoutRow, totalCents: number, memories: MemoryRow[], linked: Linked) {
  return {
    id: row.id,
    name: row.name,
    startDate: row.start_date,
    endDate: row.end_date,
    createdAt: row.created_at,
    totalCents,
    memories: memories.map(toMemoryJson),
    stashItems: linked.stashItems.map((r) => ({ id: r.id, type: r.type, title: r.title, status: r.status })),
    expenses: linked.expenses.map((r) => ({
      id: r.id,
      title: r.title,
      amountCents: r.amount_cents,
      expenseDate: r.expense_date,
      paidBy: r.paid_by,
      status: r.status,
    })),
  };
}

function groupByHangout<T extends { hangout_id: string }>(rows: T[]): Map<string, T[]> {
  const byHangout = new Map<string, T[]>();
  for (const row of rows) {
    const list = byHangout.get(row.hangout_id) ?? [];
    list.push(row);
    byHangout.set(row.hangout_id, list);
  }
  return byHangout;
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
  const [{ results: stashItems }, { results: expenses }] = await Promise.all([
    env.DB.prepare(`${LINKED_STASH_SQL} WHERE hangout_id = ? ORDER BY created_at DESC`).bind(id).all<LinkedStashRow>(),
    env.DB.prepare(`${LINKED_EXPENSES_SQL} WHERE hangout_id = ? ORDER BY expense_date DESC, created_at DESC`)
      .bind(id)
      .all<LinkedExpenseRow>(),
  ]);
  return toHangoutJson(row, totalRow?.total ?? 0, memories, { stashItems, expenses });
}

hangoutsRoutes.get('/', async (c) => {
  const [hangoutsRes, sumsRes, memoriesRes, stashRes, expensesRes] = await Promise.all([
    c.env.DB.prepare('SELECT * FROM hangouts ORDER BY created_at DESC').all<HangoutRow>(),
    c.env.DB.prepare(
      'SELECT hangout_id, SUM(amount_cents) as total FROM expenses WHERE hangout_id IS NOT NULL GROUP BY hangout_id',
    ).all<{ hangout_id: string; total: number }>(),
    c.env.DB.prepare('SELECT * FROM hangout_memories ORDER BY created_at ASC').all<MemoryRow>(),
    c.env.DB.prepare(`${LINKED_STASH_SQL} WHERE hangout_id IS NOT NULL ORDER BY created_at DESC`).all<LinkedStashRow>(),
    c.env.DB.prepare(
      `${LINKED_EXPENSES_SQL} WHERE hangout_id IS NOT NULL ORDER BY expense_date DESC, created_at DESC`,
    ).all<LinkedExpenseRow>(),
  ]);
  const hangoutRows = hangoutsRes.results;

  const totals = new Map(sumsRes.results.map((r) => [r.hangout_id, r.total]));
  const memoriesByHangout = groupByHangout(memoriesRes.results);
  const stashByHangout = groupByHangout(stashRes.results);
  const expensesByHangout = groupByHangout(expensesRes.results);

  return c.json(
    hangoutRows.map((row) =>
      toHangoutJson(row, totals.get(row.id) ?? 0, memoriesByHangout.get(row.id) ?? [], {
        stashItems: stashByHangout.get(row.id) ?? [],
        expenses: expensesByHangout.get(row.id) ?? [],
      }),
    ),
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
    c.env.DB.prepare('UPDATE stash_items SET hangout_id = NULL WHERE hangout_id = ?').bind(id),
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
