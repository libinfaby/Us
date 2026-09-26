import { Hono } from 'hono';
import type { Env } from '../types';
import { requireAuth, type AuthVariables } from '../middleware/auth';
import { notifyBoth, notifyPartner } from '../lib/notify';
import { USERNAMES } from '../lib/users';
import { isIsoDate } from '../lib/dates';

export const goalsRoutes = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

goalsRoutes.use('*', requireAuth);

const STATUSES = ['active', 'reached', 'archived'] as const;
type GoalStatus = (typeof STATUSES)[number];
const STATUS_FILTERS = [...STATUSES, 'everything'] as const;

const MAX_NOTE_LENGTH = 120;
const MAX_EMOJI_LENGTH = 16;

function isStatus(value: unknown): value is GoalStatus {
  return typeof value === 'string' && (STATUSES as readonly string[]).includes(value);
}

function isStatusFilter(value: unknown): value is (typeof STATUS_FILTERS)[number] {
  return typeof value === 'string' && (STATUS_FILTERS as readonly string[]).includes(value);
}

type GoalRow = {
  id: string;
  name: string;
  emoji: string | null;
  target_cents: number;
  target_date: string | null;
  status: GoalStatus;
  created_by: string;
  created_at: string;
};

type ContributionRow = {
  id: string;
  goal_id: string;
  username: string;
  amount_cents: number;
  note: string | null;
  created_at: string;
};

function toContributionJson(row: ContributionRow) {
  return {
    id: row.id,
    goalId: row.goal_id,
    username: row.username,
    amountCents: row.amount_cents,
    note: row.note,
    createdAt: row.created_at,
  };
}

/** [contributions] must all belong to [row], newest first. */
function toGoalJson(row: GoalRow, contributions: ContributionRow[]) {
  const byUser = Object.fromEntries(USERNAMES.map((u) => [u, 0])) as Record<string, number>;
  for (const c of contributions) byUser[c.username] = (byUser[c.username] ?? 0) + c.amount_cents;
  return {
    id: row.id,
    name: row.name,
    emoji: row.emoji,
    targetCents: row.target_cents,
    targetDate: row.target_date,
    status: row.status,
    createdBy: row.created_by,
    createdAt: row.created_at,
    savedCents: contributions.reduce((sum, c) => sum + c.amount_cents, 0),
    byUser,
    contributions: contributions.map(toContributionJson),
  };
}

async function loadGoal(env: Env, id: string) {
  const row = await env.DB.prepare('SELECT * FROM savings_goals WHERE id = ?').bind(id).first<GoalRow>();
  if (!row) return null;
  const { results } = await env.DB.prepare(
    'SELECT * FROM goal_contributions WHERE goal_id = ? ORDER BY created_at DESC, id DESC',
  )
    .bind(id)
    .all<ContributionRow>();
  return toGoalJson(row, results);
}

function formatRupees(cents: number): string {
  return `₹${Math.round(cents / 100).toLocaleString('en-IN')}`;
}

function parseEmoji(value: unknown): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null;
}

function isPositiveInt(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

goalsRoutes.get('/', async (c) => {
  const statusParam = c.req.query('status') ?? 'active';
  if (!isStatusFilter(statusParam)) {
    return c.json({ error: 'status must be active, reached, archived, or everything' }, 400);
  }
  const filtered = statusParam !== 'everything';
  const bindings = filtered ? [statusParam] : [];

  const [{ results: goalRows }, { results: contributionRows }] = await Promise.all([
    c.env.DB.prepare(`SELECT * FROM savings_goals ${filtered ? 'WHERE status = ?' : ''} ORDER BY created_at DESC, id`)
      .bind(...bindings)
      .all<GoalRow>(),
    c.env.DB.prepare(
      `SELECT gc.* FROM goal_contributions gc JOIN savings_goals g ON g.id = gc.goal_id
       ${filtered ? 'WHERE g.status = ?' : ''}
       ORDER BY gc.created_at DESC, gc.id DESC`,
    )
      .bind(...bindings)
      .all<ContributionRow>(),
  ]);

  const byGoal = new Map<string, ContributionRow[]>();
  for (const row of contributionRows) {
    const list = byGoal.get(row.goal_id) ?? [];
    list.push(row);
    byGoal.set(row.goal_id, list);
  }
  return c.json(goalRows.map((row) => toGoalJson(row, byGoal.get(row.id) ?? [])));
});

goalsRoutes.post('/', async (c) => {
  const body = await c.req.json().catch(() => null);
  const name = typeof body?.name === 'string' ? body.name.trim() : '';
  if (name.length === 0) return c.json({ error: 'name is required' }, 400);
  if (!isPositiveInt(body.targetCents)) return c.json({ error: 'targetCents must be a positive integer' }, 400);
  const emoji = parseEmoji(body.emoji);
  if (emoji !== null && emoji.length > MAX_EMOJI_LENGTH) return c.json({ error: 'emoji is too long' }, 400);
  const targetDate = body.targetDate ?? null;
  if (targetDate !== null && !isIsoDate(targetDate)) return c.json({ error: 'targetDate must be yyyy-MM-dd' }, 400);

  const id = crypto.randomUUID();
  await c.env.DB.prepare(
    'INSERT INTO savings_goals (id, name, emoji, target_cents, target_date, created_by) VALUES (?, ?, ?, ?, ?, ?)',
  )
    .bind(id, name, emoji, body.targetCents, targetDate, c.var.username)
    .run();

  c.executionCtx.waitUntil(
    notifyPartner(
      c.env,
      c.var.username,
      'New savings goal 🎯',
      `${c.var.username} started saving for ${emoji ? `${emoji} ` : ''}${name} (${formatRupees(body.targetCents)})`,
      { route: 'goals' },
    ),
  );

  return c.json(await loadGoal(c.env, id), 201);
});

goalsRoutes.patch('/:id', async (c) => {
  const id = c.req.param('id');
  const existing = await c.env.DB.prepare('SELECT * FROM savings_goals WHERE id = ?').bind(id).first<GoalRow>();
  if (!existing) return c.json({ error: 'goal not found' }, 404);

  const body = await c.req.json().catch(() => null);
  if (!body) return c.json({ error: 'invalid body' }, 400);

  const name = body.name !== undefined ? String(body.name).trim() : existing.name;
  if (name.length === 0) return c.json({ error: 'name cannot be empty' }, 400);
  const targetCents = body.targetCents !== undefined ? body.targetCents : existing.target_cents;
  if (!isPositiveInt(targetCents)) return c.json({ error: 'targetCents must be a positive integer' }, 400);
  const emoji = body.emoji !== undefined ? parseEmoji(body.emoji) : existing.emoji;
  if (emoji !== null && emoji.length > MAX_EMOJI_LENGTH) return c.json({ error: 'emoji is too long' }, 400);
  const targetDate = body.targetDate !== undefined ? body.targetDate : existing.target_date;
  if (targetDate !== null && !isIsoDate(targetDate)) return c.json({ error: 'targetDate must be yyyy-MM-dd' }, 400);
  const status = body.status !== undefined ? body.status : existing.status;
  if (!isStatus(status)) return c.json({ error: 'invalid status' }, 400);

  await c.env.DB.prepare(
    'UPDATE savings_goals SET name = ?, emoji = ?, target_cents = ?, target_date = ?, status = ? WHERE id = ?',
  )
    .bind(name, emoji, targetCents, targetDate, status, id)
    .run();
  return c.json(await loadGoal(c.env, id));
});

goalsRoutes.delete('/:id', async (c) => {
  const id = c.req.param('id');
  const existing = await c.env.DB.prepare('SELECT 1 FROM savings_goals WHERE id = ?').bind(id).first();
  if (!existing) return c.json({ error: 'goal not found' }, 404);

  await c.env.DB.batch([
    c.env.DB.prepare('DELETE FROM goal_contributions WHERE goal_id = ?').bind(id),
    c.env.DB.prepare('DELETE FROM savings_goals WHERE id = ?').bind(id),
  ]);
  return c.body(null, 204);
});

goalsRoutes.post('/:id/contributions', async (c) => {
  const goalId = c.req.param('id');
  const goal = await loadGoal(c.env, goalId);
  if (!goal) return c.json({ error: 'goal not found' }, 404);

  const body = await c.req.json().catch(() => null);
  const amountCents = body?.amountCents;
  if (typeof amountCents !== 'number' || !Number.isInteger(amountCents) || amountCents === 0) {
    return c.json({ error: 'amountCents must be a non-zero integer' }, 400);
  }
  const note = typeof body.note === 'string' && body.note.trim().length > 0 ? body.note.trim() : null;
  if (note !== null && note.length > MAX_NOTE_LENGTH) {
    return c.json({ error: `note must be at most ${MAX_NOTE_LENGTH} characters` }, 400);
  }
  const newSaved = goal.savedCents + amountCents;
  if (newSaved < 0) return c.json({ error: `only ${formatRupees(goal.savedCents)} saved so far` }, 400);

  const justReached = goal.status === 'active' && goal.savedCents < goal.targetCents && newSaved >= goal.targetCents;
  const statements = [
    c.env.DB.prepare('INSERT INTO goal_contributions (id, goal_id, username, amount_cents, note) VALUES (?, ?, ?, ?, ?)').bind(
      crypto.randomUUID(),
      goalId,
      c.var.username,
      amountCents,
      note,
    ),
  ];
  if (justReached) {
    statements.push(c.env.DB.prepare("UPDATE savings_goals SET status = 'reached' WHERE id = ?").bind(goalId));
  }
  await c.env.DB.batch(statements);

  const label = `${goal.emoji ? `${goal.emoji} ` : ''}${goal.name}`;
  const progress = `(${formatRupees(newSaved)} / ${formatRupees(goal.targetCents)})`;
  if (justReached) {
    c.executionCtx.waitUntil(
      notifyBoth(c.env, 'Goal reached 🎉', `you reached ${label} - ${formatRupees(newSaved)} saved!`, { route: 'goals' }),
    );
  } else {
    const action = amountCents > 0 ? 'added' : 'took out';
    c.executionCtx.waitUntil(
      notifyPartner(
        c.env,
        c.var.username,
        'Savings goal',
        `${c.var.username} ${action} ${formatRupees(Math.abs(amountCents))} ${amountCents > 0 ? 'to' : 'from'} ${label} ${progress}`,
        { route: 'goals' },
      ),
    );
  }

  return c.json(await loadGoal(c.env, goalId), 201);
});

goalsRoutes.delete('/:id/contributions/:contributionId', async (c) => {
  const goalId = c.req.param('id');
  const contributionId = c.req.param('contributionId');
  const existing = await c.env.DB.prepare('SELECT * FROM goal_contributions WHERE id = ? AND goal_id = ?')
    .bind(contributionId, goalId)
    .first<ContributionRow>();
  if (!existing) return c.json({ error: 'contribution not found' }, 404);
  if (existing.username !== c.var.username) {
    return c.json({ error: 'only the person who added this can delete it' }, 403);
  }

  const goal = await loadGoal(c.env, goalId);
  if (goal && goal.savedCents - existing.amount_cents < 0) {
    return c.json({ error: "can't delete this - the goal would go below ₹0" }, 400);
  }

  await c.env.DB.prepare('DELETE FROM goal_contributions WHERE id = ?').bind(contributionId).run();
  return c.json(await loadGoal(c.env, goalId));
});
