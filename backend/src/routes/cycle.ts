import { Hono, type Context } from 'hono';
import type { Env } from '../types';
import { requireAuth, type AuthVariables } from '../middleware/auth';

type CycleContext = Context<{ Bindings: Env; Variables: AuthVariables }>;

export const cycleRoutes = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

cycleRoutes.use('*', requireAuth);

const FLOWS = ['spotting', 'light', 'medium', 'heavy'] as const;
type Flow = (typeof FLOWS)[number];

function isFlow(value: unknown): value is Flow {
  return typeof value === 'string' && (FLOWS as readonly string[]).includes(value);
}

type CycleLogRow = { id: string; log_date: string; flow: Flow | null; note: string | null; created_at: string };

function toLogJson(row: CycleLogRow) {
  return { id: row.id, logDate: row.log_date, flow: row.flow, note: row.note, createdAt: row.created_at };
}

const MS_PER_DAY = 24 * 60 * 60 * 1000;

function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function daysBetween(fromIso: string, toIso: string): number {
  const from = new Date(`${fromIso}T00:00:00Z`).getTime();
  const to = new Date(`${toIso}T00:00:00Z`).getTime();
  return Math.round((to - from) / MS_PER_DAY);
}

/** Groups consecutive flow-logged dates into periods, returning them oldest-first. */
function computePeriods(logs: { log_date: string; flow: Flow | null }[]): { start: string; end: string }[] {
  const flowDates = logs.filter((l) => l.flow !== null).map((l) => l.log_date).sort();
  const periods: { start: string; end: string }[] = [];
  for (const date of flowDates) {
    const last = periods[periods.length - 1];
    if (last && daysBetween(last.end, date) === 1) {
      last.end = date;
    } else {
      periods.push({ start: date, end: date });
    }
  }
  return periods;
}

async function requireTrackedUser(c: CycleContext): Promise<Response | null> {
  const settings = await c.env.DB.prepare('SELECT tracked_user FROM cycle_settings WHERE id = 1').first<{
    tracked_user: string;
  }>();
  if (c.var.username !== settings?.tracked_user) {
    return c.json({ error: 'only the tracked user can do this' }, 403);
  }
  return null;
}

cycleRoutes.get('/status', async (c) => {
  const settings = await c.env.DB.prepare(
    'SELECT tracked_user, avg_cycle_length, avg_period_length FROM cycle_settings WHERE id = 1',
  ).first<{ tracked_user: string; avg_cycle_length: number; avg_period_length: number }>();

  const { results } = await c.env.DB.prepare('SELECT log_date, flow FROM cycle_logs ORDER BY log_date ASC').all<{
    log_date: string;
    flow: Flow | null;
  }>();

  const periods = computePeriods(results);
  const lastPeriod = periods[periods.length - 1];
  const today = new Date().toISOString().slice(0, 10);

  if (!lastPeriod || !settings) {
    return c.json({
      trackedUser: settings?.tracked_user ?? null,
      currentDay: null,
      onPeriod: false,
      statusLabel: 'not tracked yet',
      predictedNextDate: null,
    });
  }

  const currentDay = daysBetween(lastPeriod.start, today) + 1;
  const onPeriod = today >= lastPeriod.start && today <= lastPeriod.end;
  const predictedNextDate = addDays(lastPeriod.start, settings.avg_cycle_length);

  return c.json({
    trackedUser: settings.tracked_user,
    currentDay,
    onPeriod,
    statusLabel: onPeriod ? 'on your period' : 'smooth sailing',
    predictedNextDate,
  });
});

cycleRoutes.get('/logs', async (c) => {
  const { results } = await c.env.DB.prepare('SELECT * FROM cycle_logs ORDER BY log_date DESC').all<CycleLogRow>();
  return c.json(results.map(toLogJson));
});

cycleRoutes.post('/logs', async (c) => {
  const forbidden = await requireTrackedUser(c);
  if (forbidden) return forbidden;

  const body = await c.req.json().catch(() => null);
  const { logDate, flow, note } = body ?? {};
  if (typeof logDate !== 'string' || logDate.trim().length === 0 || !isFlow(flow)) {
    return c.json({ error: 'logDate and a valid flow (spotting/light/medium/heavy) are required' }, 400);
  }

  const existing = await c.env.DB.prepare('SELECT id FROM cycle_logs WHERE log_date = ?')
    .bind(logDate)
    .first<{ id: string }>();
  const id = existing?.id ?? crypto.randomUUID();

  await c.env.DB.prepare(
    `INSERT INTO cycle_logs (id, log_date, flow, note) VALUES (?, ?, ?, ?)
     ON CONFLICT(log_date) DO UPDATE SET flow = excluded.flow, note = excluded.note`,
  )
    .bind(id, logDate, flow, typeof note === 'string' ? note : null)
    .run();

  const row = await c.env.DB.prepare('SELECT * FROM cycle_logs WHERE id = ?').bind(id).first<CycleLogRow>();
  return c.json(toLogJson(row!), existing ? 200 : 201);
});

cycleRoutes.delete('/logs/:id', async (c) => {
  const forbidden = await requireTrackedUser(c);
  if (forbidden) return forbidden;

  const id = c.req.param('id');
  const result = await c.env.DB.prepare('DELETE FROM cycle_logs WHERE id = ?').bind(id).run();
  if (result.meta.changes === 0) return c.json({ error: 'log not found' }, 404);
  return c.json({ ok: true });
});
