import { Hono } from 'hono';
import type { Env } from '../types';
import { requireAuth, type AuthVariables } from '../middleware/auth';
import { notifyPartner } from '../lib/notify';
import { daysBetween, isIsoDate, nextAnniversary, todayIst } from '../lib/dates';

export const datesRoutes = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

datesRoutes.use('*', requireAuth);

const KINDS = ['countdown', 'milestone'] as const;
type DateKind = (typeof KINDS)[number];

const MAX_EMOJI_LENGTH = 16;

function isKind(value: unknown): value is DateKind {
  return typeof value === 'string' && (KINDS as readonly string[]).includes(value);
}

type SpecialDateRow = {
  id: string;
  kind: DateKind;
  title: string;
  emoji: string | null;
  date: string;
  created_by: string;
  created_at: string;
};

function toDateJson(row: SpecialDateRow, today: string) {
  const base = {
    id: row.id,
    kind: row.kind,
    title: row.title,
    emoji: row.emoji,
    date: row.date,
    createdBy: row.created_by,
    createdAt: row.created_at,
  };
  if (row.kind === 'countdown') {
    const daysUntil = daysBetween(today, row.date);
    return { ...base, daysUntil, isPast: daysUntil < 0, years: null, nextAnniversary: null, daysUntilNext: null };
  }
  const next = nextAnniversary(row.date, today);
  const yearsSoFar = next.date === today ? next.years : next.years - 1;
  return {
    ...base,
    daysUntil: null,
    isPast: false,
    years: yearsSoFar,
    nextAnniversary: next.date,
    daysUntilNext: daysBetween(today, next.date),
  };
}

/** Upcoming countdowns (soonest first), then milestones (next anniversary first), then past countdowns. */
function sortKey(json: ReturnType<typeof toDateJson>): [number, number] {
  if (json.kind === 'countdown') return json.isPast ? [2, -json.daysUntil!] : [0, json.daysUntil!];
  return [1, json.daysUntilNext!];
}

type ValidatedFields = { kind: DateKind; title: string; emoji: string | null; date: string };

/** [today] is null to skip the past/future rule - see the PATCH handler. */
function validate(fields: ValidatedFields, today: string | null): string | null {
  if (fields.title.length === 0) return 'title is required';
  if (fields.emoji !== null && fields.emoji.length > MAX_EMOJI_LENGTH) return 'emoji is too long';
  if (!isIsoDate(fields.date)) return 'date must be yyyy-MM-dd';
  if (today === null) return null;
  if (fields.kind === 'countdown' && fields.date < today) return "a countdown's date can't be in the past";
  if (fields.kind === 'milestone' && fields.date > today) return "a milestone's date can't be in the future";
  return null;
}

function parseEmoji(value: unknown): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null;
}

datesRoutes.get('/', async (c) => {
  const today = todayIst();
  const { results } = await c.env.DB.prepare('SELECT * FROM special_dates').all<SpecialDateRow>();
  const items = results.map((row) => toDateJson(row, today));
  items.sort((a, b) => {
    const [ga, ka] = sortKey(a);
    const [gb, kb] = sortKey(b);
    return ga - gb || ka - kb || a.title.localeCompare(b.title);
  });
  return c.json(items);
});

datesRoutes.post('/', async (c) => {
  const body = await c.req.json().catch(() => null);
  if (!body || !isKind(body.kind)) return c.json({ error: 'kind must be countdown or milestone' }, 400);

  const fields: ValidatedFields = {
    kind: body.kind,
    title: typeof body.title === 'string' ? body.title.trim() : '',
    emoji: parseEmoji(body.emoji),
    date: body.date,
  };
  const today = todayIst();
  const error = validate(fields, today);
  if (error) return c.json({ error }, 400);

  const id = crypto.randomUUID();
  await c.env.DB.prepare('INSERT INTO special_dates (id, kind, title, emoji, date, created_by) VALUES (?, ?, ?, ?, ?, ?)')
    .bind(id, fields.kind, fields.title, fields.emoji, fields.date, c.var.username)
    .run();
  const row = await c.env.DB.prepare('SELECT * FROM special_dates WHERE id = ?').bind(id).first<SpecialDateRow>();

  const label = fields.kind === 'countdown' ? 'a countdown' : 'a date to remember';
  c.executionCtx.waitUntil(
    notifyPartner(c.env, c.var.username, 'New date 📅', `${c.var.username} added ${label}: ${fields.title}`, {
      route: 'dates',
    }),
  );

  return c.json(toDateJson(row!, today), 201);
});

datesRoutes.patch('/:id', async (c) => {
  const id = c.req.param('id');
  const existing = await c.env.DB.prepare('SELECT * FROM special_dates WHERE id = ?').bind(id).first<SpecialDateRow>();
  if (!existing) return c.json({ error: 'date not found' }, 404);

  const body = await c.req.json().catch(() => null);
  if (!body) return c.json({ error: 'invalid body' }, 400);
  if (body.kind !== undefined && !isKind(body.kind)) return c.json({ error: 'kind must be countdown or milestone' }, 400);

  const fields: ValidatedFields = {
    kind: body.kind ?? existing.kind,
    title: body.title !== undefined ? String(body.title).trim() : existing.title,
    emoji: body.emoji !== undefined ? parseEmoji(body.emoji) : existing.emoji,
    date: body.date ?? existing.date,
  };
  const today = todayIst();
  // Only re-check the past/future rule when the date or kind actually changes, so a countdown
  // that has already gone by can still be renamed.
  const dateOrKindChanged = fields.date !== existing.date || fields.kind !== existing.kind;
  const error = validate(fields, dateOrKindChanged ? today : null);
  if (error) return c.json({ error }, 400);

  await c.env.DB.prepare('UPDATE special_dates SET kind = ?, title = ?, emoji = ?, date = ? WHERE id = ?')
    .bind(fields.kind, fields.title, fields.emoji, fields.date, id)
    .run();
  const row = await c.env.DB.prepare('SELECT * FROM special_dates WHERE id = ?').bind(id).first<SpecialDateRow>();
  return c.json(toDateJson(row!, today));
});

datesRoutes.delete('/:id', async (c) => {
  const id = c.req.param('id');
  const result = await c.env.DB.prepare('DELETE FROM special_dates WHERE id = ?').bind(id).run();
  if (result.meta.changes === 0) return c.json({ error: 'date not found' }, 404);
  return c.json({ ok: true });
});
