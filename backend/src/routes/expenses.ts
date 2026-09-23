import { Hono } from 'hono';
import type { Env } from '../types';
import { requireAuth, type AuthVariables } from '../middleware/auth';
import { isUsername, type Username } from '../lib/users';
import { equalSplit, validateCustomSplit } from '../lib/split';
import { notifyPartner } from '../lib/notify';

export const expensesRoutes = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

expensesRoutes.use('*', requireAuth);

type ExpenseRow = {
  id: string;
  title: string;
  subtitle: string | null;
  amount_cents: number;
  currency: string;
  expense_date: string;
  location: string | null;
  is_recurring: number;
  cadence: string | null;
  hangout_id: string | null;
  category: string | null;
  paid_by: Username;
  split_type: string;
  split_json: string;
  status: string;
  created_at: string;
  updated_at: string;
};

function toExpenseJson(row: ExpenseRow) {
  return {
    id: row.id,
    title: row.title,
    subtitle: row.subtitle,
    amountCents: row.amount_cents,
    currency: row.currency,
    expenseDate: row.expense_date,
    location: row.location,
    isRecurring: row.is_recurring === 1,
    cadence: row.cadence,
    hangoutId: row.hangout_id,
    category: row.category,
    paidBy: row.paid_by,
    splitType: row.split_type,
    split: JSON.parse(row.split_json) as Record<Username, number>,
    status: row.status,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

const STATUS_FILTERS = ['open', 'settled', 'everything'] as const;
type StatusFilter = (typeof STATUS_FILTERS)[number];

function isStatusFilter(value: unknown): value is StatusFilter {
  return typeof value === 'string' && (STATUS_FILTERS as readonly string[]).includes(value);
}

/** Shared shape for both creating and (partially) editing an expense. */
type ExpenseBody = {
  title?: unknown;
  amountCents?: unknown;
  expenseDate?: unknown;
  subtitle?: unknown;
  location?: unknown;
  hangoutId?: unknown;
  category?: unknown;
  paidBy?: unknown;
  splitType?: unknown;
  split?: unknown;
  isRecurring?: unknown;
  cadence?: unknown;
};

async function hangoutExists(env: Env, hangoutId: string): Promise<boolean> {
  const row = await env.DB.prepare('SELECT id FROM hangouts WHERE id = ?').bind(hangoutId).first();
  return row !== null;
}

expensesRoutes.get('/', async (c) => {
  const statusParam = c.req.query('status') ?? 'open';
  if (!isStatusFilter(statusParam)) {
    return c.json({ error: 'status must be open, settled, or everything' }, 400);
  }
  const hangoutId = c.req.query('hangoutId');

  const conditions: string[] = [];
  const bindings: string[] = [];
  if (statusParam !== 'everything') {
    conditions.push('status = ?');
    bindings.push(statusParam);
  }
  if (hangoutId) {
    conditions.push('hangout_id = ?');
    bindings.push(hangoutId);
  }
  const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';

  const { results } = await c.env.DB.prepare(
    `SELECT * FROM expenses ${where} ORDER BY expense_date DESC, created_at DESC`,
  )
    .bind(...bindings)
    .all<ExpenseRow>();
  return c.json(results.map(toExpenseJson));
});

expensesRoutes.post('/', async (c) => {
  const body = (await c.req.json().catch(() => null)) as ExpenseBody | null;
  const { title, amountCents, expenseDate, subtitle, location, hangoutId, category, isRecurring, cadence } =
    body ?? {};

  if (
    typeof title !== 'string' ||
    title.trim().length === 0 ||
    typeof amountCents !== 'number' ||
    !Number.isInteger(amountCents) ||
    amountCents <= 0 ||
    typeof expenseDate !== 'string' ||
    expenseDate.trim().length === 0
  ) {
    return c.json({ error: 'title, positive integer amountCents, and expenseDate are required' }, 400);
  }

  const paidBy = body?.paidBy !== undefined ? body.paidBy : c.var.username;
  if (!isUsername(paidBy)) {
    return c.json({ error: 'paidBy must be pingu or codu' }, 400);
  }

  const splitType = body?.splitType !== undefined ? body.splitType : 'equal';
  if (splitType !== 'equal' && splitType !== 'custom') {
    return c.json({ error: 'splitType must be equal or custom' }, 400);
  }
  let split: Record<Username, number>;
  if (splitType === 'custom') {
    const validated = validateCustomSplit(amountCents, body?.split);
    if (!validated) {
      return c.json(
        { error: 'custom split amounts must be non-negative integers for pingu and codu that sum to amountCents' },
        400,
      );
    }
    split = validated;
  } else {
    split = equalSplit(amountCents, paidBy);
  }

  if (typeof hangoutId === 'string' && hangoutId.length > 0 && !(await hangoutExists(c.env, hangoutId))) {
    return c.json({ error: 'unknown hangoutId' }, 400);
  }

  const id = crypto.randomUUID();
  await c.env.DB.prepare(
    `INSERT INTO expenses
       (id, title, subtitle, amount_cents, expense_date, location, is_recurring, cadence, hangout_id, category, paid_by, split_type, split_json)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  )
    .bind(
      id,
      title.trim(),
      typeof subtitle === 'string' ? subtitle : null,
      amountCents,
      expenseDate,
      typeof location === 'string' ? location : null,
      isRecurring ? 1 : 0,
      typeof cadence === 'string' ? cadence : null,
      typeof hangoutId === 'string' && hangoutId.length > 0 ? hangoutId : null,
      typeof category === 'string' ? category : null,
      paidBy,
      splitType,
      JSON.stringify(split),
    )
    .run();

  const row = await c.env.DB.prepare('SELECT * FROM expenses WHERE id = ?').bind(id).first<ExpenseRow>();

  c.executionCtx.waitUntil(
    notifyPartner(
      c.env,
      c.var.username,
      'New expense',
      `${c.var.username} added '${title.trim()}' - ₹${(amountCents / 100).toFixed(2)}`,
      { route: 'money' },
    ),
  );

  return c.json(toExpenseJson(row!), 201);
});

expensesRoutes.patch('/:id', async (c) => {
  const id = c.req.param('id');
  const existing = await c.env.DB.prepare('SELECT * FROM expenses WHERE id = ?').bind(id).first<ExpenseRow>();
  if (!existing) return c.json({ error: 'expense not found' }, 404);

  const body = (await c.req.json().catch(() => null)) as ExpenseBody | null;
  if (!body) return c.json({ error: 'invalid body' }, 400);

  const title = typeof body.title === 'string' ? body.title.trim() : existing.title;
  if (title.length === 0) return c.json({ error: 'title cannot be empty' }, 400);

  const amountCents = body.amountCents !== undefined ? body.amountCents : existing.amount_cents;
  if (typeof amountCents !== 'number' || !Number.isInteger(amountCents) || amountCents <= 0) {
    return c.json({ error: 'amountCents must be a positive integer' }, 400);
  }

  const expenseDate = typeof body.expenseDate === 'string' && body.expenseDate.length > 0
    ? body.expenseDate
    : existing.expense_date;

  const paidBy = body.paidBy !== undefined ? body.paidBy : existing.paid_by;
  if (!isUsername(paidBy)) return c.json({ error: 'paidBy must be pingu or codu' }, 400);

  const splitType = body.splitType !== undefined ? body.splitType : existing.split_type;
  if (splitType !== 'equal' && splitType !== 'custom') {
    return c.json({ error: 'splitType must be equal or custom' }, 400);
  }

  let split: Record<Username, number>;
  if (splitType === 'custom') {
    // Always recompute against the merged amount - never trust a stored split
    // against a possibly-changed amount.
    const source = body.split !== undefined ? body.split : JSON.parse(existing.split_json);
    const validated = validateCustomSplit(amountCents, source);
    if (!validated) {
      return c.json(
        { error: 'custom split amounts must be non-negative integers for pingu and codu that sum to amountCents' },
        400,
      );
    }
    split = validated;
  } else {
    split = equalSplit(amountCents, paidBy);
  }

  const hangoutId = body.hangoutId !== undefined ? body.hangoutId : existing.hangout_id;
  if (typeof hangoutId === 'string' && hangoutId.length > 0 && !(await hangoutExists(c.env, hangoutId))) {
    return c.json({ error: 'unknown hangoutId' }, 400);
  }

  const subtitle = body.subtitle !== undefined ? body.subtitle : existing.subtitle;
  const location = body.location !== undefined ? body.location : existing.location;
  const category = body.category !== undefined ? body.category : existing.category;
  const isRecurring = body.isRecurring !== undefined ? (body.isRecurring ? 1 : 0) : existing.is_recurring;
  const cadence = body.cadence !== undefined ? body.cadence : existing.cadence;

  await c.env.DB.prepare(
    `UPDATE expenses SET
       title = ?, subtitle = ?, amount_cents = ?, expense_date = ?, location = ?,
       is_recurring = ?, cadence = ?, hangout_id = ?, category = ?, paid_by = ?,
       split_type = ?, split_json = ?, updated_at = datetime('now')
     WHERE id = ?`,
  )
    .bind(
      title,
      typeof subtitle === 'string' ? subtitle : null,
      amountCents,
      expenseDate,
      typeof location === 'string' ? location : null,
      isRecurring,
      typeof cadence === 'string' ? cadence : null,
      typeof hangoutId === 'string' && hangoutId.length > 0 ? hangoutId : null,
      typeof category === 'string' ? category : null,
      paidBy,
      splitType,
      JSON.stringify(split),
      id,
    )
    .run();

  const row = await c.env.DB.prepare('SELECT * FROM expenses WHERE id = ?').bind(id).first<ExpenseRow>();
  return c.json(toExpenseJson(row!));
});

expensesRoutes.post('/:id/settle', async (c) => {
  const id = c.req.param('id');
  const existing = await c.env.DB.prepare('SELECT * FROM expenses WHERE id = ?').bind(id).first<ExpenseRow>();
  if (!existing) return c.json({ error: 'expense not found' }, 404);

  const nextStatus = existing.status === 'settled' ? 'open' : 'settled';
  await c.env.DB.prepare("UPDATE expenses SET status = ?, updated_at = datetime('now') WHERE id = ?")
    .bind(nextStatus, id)
    .run();
  const row = await c.env.DB.prepare('SELECT * FROM expenses WHERE id = ?').bind(id).first<ExpenseRow>();
  return c.json(toExpenseJson(row!));
});

expensesRoutes.post('/settle-all', async (c) => {
  const body = await c.req.json().catch(() => null);
  const hangoutId = body?.hangoutId;

  const result =
    typeof hangoutId === 'string' && hangoutId.length > 0
      ? await c.env.DB.prepare(
          "UPDATE expenses SET status = 'settled', updated_at = datetime('now') WHERE status = 'open' AND hangout_id = ?",
        )
          .bind(hangoutId)
          .run()
      : await c.env.DB.prepare(
          "UPDATE expenses SET status = 'settled', updated_at = datetime('now') WHERE status = 'open'",
        ).run();

  return c.json({ ok: true, settledCount: result.meta.changes });
});

expensesRoutes.delete('/:id', async (c) => {
  const id = c.req.param('id');
  const result = await c.env.DB.prepare('DELETE FROM expenses WHERE id = ?').bind(id).run();
  if (result.meta.changes === 0) return c.json({ error: 'expense not found' }, 404);
  return c.json({ ok: true });
});

/**
 * Net balance across all open expenses, independent of whatever status filter
 * the list view is currently showing. A positive value for a user means
 * they're owed that many cents overall; since there are only ever two users,
 * pingu's net is always -codu's net.
 */
expensesRoutes.get('/balance', async (c) => {
  const { results } = await c.env.DB.prepare(
    "SELECT amount_cents, paid_by, split_json FROM expenses WHERE status = 'open'",
  ).all<{ amount_cents: number; paid_by: Username; split_json: string }>();

  const net: Record<Username, number> = { pingu: 0, codu: 0 };
  for (const row of results) {
    const split = JSON.parse(row.split_json) as Record<Username, number>;
    net[row.paid_by] += row.amount_cents;
    for (const user of ['pingu', 'codu'] as const) net[user] -= split[user] ?? 0;
  }

  return c.json({ net });
});
