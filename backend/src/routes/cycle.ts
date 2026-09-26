import { Hono, type Context } from 'hono';
import type { Env } from '../types';
import { requireAuth, type AuthVariables } from '../middleware/auth';
import { notifyPartner } from '../lib/notify';

type CycleContext = Context<{ Bindings: Env; Variables: AuthVariables }>;

export const cycleRoutes = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

cycleRoutes.use('*', requireAuth);

const FLOWS = ['spotting', 'light', 'medium', 'heavy'] as const;
type Flow = (typeof FLOWS)[number];

const SYMPTOM_TAGS = ['cramps', 'headache', 'bloating', 'fatigue', 'backache', 'nausea', 'tender breasts', 'acne'] as const;
const MOOD_TAGS = ['happy', 'sad', 'anxious', 'irritable', 'calm', 'sensitive', 'naughty'] as const;
const ALL_TAGS: readonly string[] = [...SYMPTOM_TAGS, ...MOOD_TAGS];

const BEHAVIOR_TAGS = ['sweet', 'moody', 'clingy', 'chill', 'grumpy', 'loving', 'distant', 'funny', 'needy', 'thoughtful'] as const;

const PMS_WINDOW_DAYS = 5;
const OVULATION_OFFSET_DAYS = 14;
const IRREGULAR_CYCLE_LENGTH_DELTA = 7;
const IRREGULAR_PERIOD_LENGTH_DELTA = 3;
const MIN_PERIODS_FOR_IRREGULARITY = 3;

function isFlow(value: unknown): value is Flow {
  return typeof value === 'string' && (FLOWS as readonly string[]).includes(value);
}

function isValidTags(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((t) => typeof t === 'string' && ALL_TAGS.includes(t));
}

function isValidBehaviorTags(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((t) => typeof t === 'string' && (BEHAVIOR_TAGS as readonly string[]).includes(t));
}

type ObservationRow = { obs_date: string; tags_json: string; note: string | null };

function toObservationJson(row: ObservationRow) {
  return { obsDate: row.obs_date, tags: parseTags(row.tags_json), note: row.note };
}

type CycleLogRow = {
  id: string;
  log_date: string;
  flow: Flow | null;
  symptoms_json: string;
  note: string | null;
  partner_note: string | null;
  created_at: string;
};

function toLogJson(row: CycleLogRow) {
  return {
    id: row.id,
    logDate: row.log_date,
    flow: row.flow,
    tags: parseTags(row.symptoms_json),
    note: row.note,
    partnerNote: row.partner_note,
    createdAt: row.created_at,
  };
}

function parseTags(json: string): string[] {
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? parsed.filter((t): t is string => typeof t === 'string') : [];
  } catch {
    return [];
  }
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

function average(values: number[]): number {
  return Math.round(values.reduce((sum, v) => sum + v, 0) / values.length);
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

function periodLengthDays(period: { start: string; end: string }): number {
  return daysBetween(period.start, period.end) + 1;
}

/** Cycle length = days between consecutive period starts. */
function cycleLengths(periods: { start: string; end: string }[]): number[] {
  const lengths: number[] = [];
  for (let i = 1; i < periods.length; i++) {
    lengths.push(daysBetween(periods[i - 1].start, periods[i].start));
  }
  return lengths;
}

function computeIrregularity(
  periods: { start: string; end: string }[],
  avgCycleLength: number,
  avgPeriodLength: number,
): { message: string } | null {
  if (periods.length < MIN_PERIODS_FOR_IRREGULARITY) return null;

  const lengths = cycleLengths(periods);
  const lastCycleLength = lengths[lengths.length - 1];
  const lastPeriodLength = periodLengthDays(periods[periods.length - 1]);

  if (lastCycleLength !== undefined && Math.abs(lastCycleLength - avgCycleLength) > IRREGULAR_CYCLE_LENGTH_DELTA) {
    return { message: `last cycle was ${lastCycleLength} days, vs your usual ${avgCycleLength}, worth keeping an eye on` };
  }
  if (Math.abs(lastPeriodLength - avgPeriodLength) > IRREGULAR_PERIOD_LENGTH_DELTA) {
    return { message: `last period lasted ${lastPeriodLength} days, vs your usual ${avgPeriodLength}, worth keeping an eye on` };
  }
  return null;
}

type Phase = 'menstrual' | 'follicular' | 'ovulation' | 'luteal' | 'pms';

function computePhase(
  today: string,
  onPeriod: boolean,
  pmsWindowActive: boolean,
  predictedNextDate: string,
): Phase {
  if (onPeriod) return 'menstrual';
  if (pmsWindowActive) return 'pms';
  const ovulationDate = addDays(predictedNextDate, -OVULATION_OFFSET_DAYS);
  if (Math.abs(daysBetween(ovulationDate, today)) <= 1) return 'ovulation';
  return today < ovulationDate ? 'follicular' : 'luteal';
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

/** Inverse of requireTrackedUser - observations are logged by whoever ISN'T the tracked user. */
async function requirePartnerUser(c: CycleContext): Promise<Response | null> {
  const settings = await c.env.DB.prepare('SELECT tracked_user FROM cycle_settings WHERE id = 1').first<{
    tracked_user: string;
  }>();
  if (!settings || c.var.username === settings.tracked_user) {
    return c.json({ error: 'only the partner can do this' }, 403);
  }
  return null;
}

cycleRoutes.get('/status', async (c) => {
  const settings = await c.env.DB.prepare(
    'SELECT tracked_user, avg_cycle_length, avg_period_length FROM cycle_settings WHERE id = 1',
  ).first<{ tracked_user: string; avg_cycle_length: number; avg_period_length: number }>();

  const { results } = await c.env.DB.prepare('SELECT log_date, flow, symptoms_json, partner_note FROM cycle_logs ORDER BY log_date ASC').all<{
    log_date: string;
    flow: Flow | null;
    symptoms_json: string;
    partner_note: string | null;
  }>();

  const periods = computePeriods(results);
  const lastPeriod = periods[periods.length - 1];
  const today = new Date().toISOString().slice(0, 10);
  const latestRow = results[results.length - 1];
  const latestEntry = latestRow
    ? { logDate: latestRow.log_date, tags: parseTags(latestRow.symptoms_json), partnerNote: latestRow.partner_note }
    : null;

  const latestObsRow = await c.env.DB.prepare('SELECT obs_date, tags_json, note FROM partner_observations ORDER BY obs_date DESC LIMIT 1').first<ObservationRow>();
  const observation = latestObsRow ? toObservationJson(latestObsRow) : null;

  if (!lastPeriod || !settings) {
    return c.json({
      trackedUser: settings?.tracked_user ?? null,
      currentDay: null,
      onPeriod: false,
      statusLabel: 'not tracked yet',
      predictedNextDate: null,
      phase: null,
      daysUntilNextPeriod: null,
      avgCycleLength: null,
      avgPeriodLength: null,
      pmsWindowActive: false,
      irregularityMessage: null,
      latestEntry,
      observation,
    });
  }

  const lengths = cycleLengths(periods);
  const avgCycleLength = lengths.length > 0 ? average(lengths) : settings.avg_cycle_length;
  const avgPeriodLength = average(periods.map(periodLengthDays));

  const currentDay = daysBetween(lastPeriod.start, today) + 1;
  const onPeriod = today >= lastPeriod.start && today <= lastPeriod.end;
  const predictedNextDate = addDays(lastPeriod.start, avgCycleLength);
  const daysUntilNextPeriod = daysBetween(today, predictedNextDate);
  const pmsWindowActive = !onPeriod && daysUntilNextPeriod >= 1 && daysUntilNextPeriod <= PMS_WINDOW_DAYS;
  const phase = computePhase(today, onPeriod, pmsWindowActive, predictedNextDate);
  const irregularity = computeIrregularity(periods, avgCycleLength, avgPeriodLength);

  return c.json({
    trackedUser: settings.tracked_user,
    currentDay,
    onPeriod,
    statusLabel: onPeriod ? 'on your period' : 'smooth sailing',
    predictedNextDate,
    phase,
    daysUntilNextPeriod,
    avgCycleLength,
    avgPeriodLength,
    pmsWindowActive,
    irregularityMessage: irregularity?.message ?? null,
    latestEntry,
    observation,
  });
});

cycleRoutes.get('/logs', async (c) => {
  const forbidden = await requireTrackedUser(c);
  if (forbidden) return forbidden;

  const { results } = await c.env.DB.prepare('SELECT * FROM cycle_logs ORDER BY log_date DESC').all<CycleLogRow>();
  return c.json(results.map(toLogJson));
});

cycleRoutes.post('/logs', async (c) => {
  const forbidden = await requireTrackedUser(c);
  if (forbidden) return forbidden;

  const body = await c.req.json().catch(() => null);
  const { logDate, flow, note, tags, partnerNote } = body ?? {};
  if (typeof logDate !== 'string' || logDate.trim().length === 0) {
    return c.json({ error: 'logDate is required' }, 400);
  }
  // flow is optional so mood/symptoms can be logged outside a period; null means "no period".
  if (flow != null && !isFlow(flow)) {
    return c.json({ error: 'flow must be one of spotting/light/medium/heavy, or omitted' }, 400);
  }
  const tagList = tags === undefined ? [] : tags;
  if (!isValidTags(tagList)) {
    return c.json({ error: `tags must be from: ${ALL_TAGS.join(', ')}` }, 400);
  }
  const hasText = (v: unknown) => typeof v === 'string' && v.trim().length > 0;
  if (flow == null && tagList.length === 0 && !hasText(note) && !hasText(partnerNote)) {
    return c.json({ error: 'pick a flow, a tag, or add a note' }, 400);
  }

  const existing = await c.env.DB.prepare('SELECT id FROM cycle_logs WHERE log_date = ?')
    .bind(logDate)
    .first<{ id: string }>();
  const id = existing?.id ?? crypto.randomUUID();

  await c.env.DB.prepare(
    `INSERT INTO cycle_logs (id, log_date, flow, symptoms_json, note, partner_note) VALUES (?, ?, ?, ?, ?, ?)
     ON CONFLICT(log_date) DO UPDATE SET flow = excluded.flow, symptoms_json = excluded.symptoms_json, note = excluded.note, partner_note = excluded.partner_note`,
  )
    .bind(
      id,
      logDate,
      isFlow(flow) ? flow : null,
      JSON.stringify(tagList),
      typeof note === 'string' ? note : null,
      typeof partnerNote === 'string' ? partnerNote : null,
    )
    .run();

  const row = await c.env.DB.prepare('SELECT * FROM cycle_logs WHERE id = ?').bind(id).first<CycleLogRow>();

  if (!existing) {
    // No symptom/flow/note content in the push - it can render on a lock screen.
    c.executionCtx.waitUntil(
      notifyPartner(c.env, c.var.username, 'Cycle update', `${c.var.username} logged today's cycle entry`, {
        route: 'cycle',
      }),
    );
  }

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

cycleRoutes.get('/observations', async (c) => {
  const forbidden = await requirePartnerUser(c);
  if (forbidden) return forbidden;

  const { results } = await c.env.DB.prepare('SELECT obs_date, tags_json, note FROM partner_observations ORDER BY obs_date DESC').all<ObservationRow>();
  return c.json(results.map(toObservationJson));
});

cycleRoutes.post('/observations', async (c) => {
  const forbidden = await requirePartnerUser(c);
  if (forbidden) return forbidden;

  const body = await c.req.json().catch(() => null);
  const { tags, note, date } = body ?? {};
  const tagList = tags === undefined ? [] : tags;
  if (!isValidBehaviorTags(tagList)) {
    return c.json({ error: `tags must be from: ${BEHAVIOR_TAGS.join(', ')}` }, 400);
  }
  const obsDate = typeof date === 'string' && date.trim().length > 0 ? date : new Date().toISOString().slice(0, 10);

  const existing = await c.env.DB.prepare('SELECT id FROM partner_observations WHERE obs_date = ?')
    .bind(obsDate)
    .first<{ id: string }>();

  const id = existing?.id ?? crypto.randomUUID();
  await c.env.DB.prepare(
    `INSERT INTO partner_observations (id, obs_date, tags_json, note) VALUES (?, ?, ?, ?)
     ON CONFLICT(obs_date) DO UPDATE SET tags_json = excluded.tags_json, note = excluded.note`,
  )
    .bind(id, obsDate, JSON.stringify(tagList), typeof note === 'string' ? note : null)
    .run();

  const row = await c.env.DB.prepare('SELECT obs_date, tags_json, note FROM partner_observations WHERE obs_date = ?')
    .bind(obsDate)
    .first<ObservationRow>();

  if (!existing) {
    // No tags/note content in the push - it can render on a lock screen.
    c.executionCtx.waitUntil(
      notifyPartner(c.env, c.var.username, 'A note about your day', `${c.var.username} left you a note today`, {
        route: 'cycle',
      }),
    );
  }

  return c.json(toObservationJson(row!));
});

cycleRoutes.delete('/observations/:date', async (c) => {
  const forbidden = await requirePartnerUser(c);
  if (forbidden) return forbidden;

  const date = c.req.param('date');
  const result = await c.env.DB.prepare('DELETE FROM partner_observations WHERE obs_date = ?').bind(date).run();
  if (result.meta.changes === 0) return c.json({ error: 'nothing logged for that day' }, 404);
  return c.json({ ok: true });
});
