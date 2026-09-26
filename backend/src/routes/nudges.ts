import { Hono } from 'hono';
import type { Env } from '../types';
import { requireAuth, type AuthVariables } from '../middleware/auth';
import { notifyPartner } from '../lib/notify';

export const nudgesRoutes = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

nudgesRoutes.use('*', requireAuth);

const MAX_MESSAGE_LENGTH = 80;
const COOLDOWN_SECONDS = 10;

const RANDOM_NUDGES = [
  'thinking of you 💭',
  'miss you 🥺',
  'sending you a hug 🤗',
  'you + me = 🐧💛',
  'just wanted to say hi 👋',
  'you make my day better ☀️',
  'boop 👉👃',
  'can we cuddle rn 🫂',
  'proud of you, always 💪',
  "you're my favourite person 💕",
  'hope your day is going great 🌸',
  'counting down till I see you ⏳',
  'here is a virtual kiss 😘',
  "don't forget to drink water 💧",
  'you + snacks + me later? 🍿',
];

type NudgeRow = { id: string; sender: string; message: string; created_at: string };

function toNudgeJson(row: NudgeRow) {
  return { id: row.id, sender: row.sender, message: row.message, createdAt: row.created_at };
}

nudgesRoutes.post('/', async (c) => {
  const body = await c.req.json().catch(() => ({}));
  const rawMessage = body?.message;
  if (rawMessage !== undefined && rawMessage !== null && typeof rawMessage !== 'string') {
    return c.json({ error: 'message must be a string' }, 400);
  }

  let message: string;
  if (typeof rawMessage === 'string') {
    message = rawMessage.trim();
    if (message.length === 0) return c.json({ error: 'message cannot be empty' }, 400);
    if (message.length > MAX_MESSAGE_LENGTH) {
      return c.json({ error: `message must be at most ${MAX_MESSAGE_LENGTH} characters` }, 400);
    }
  } else {
    message = RANDOM_NUDGES[Math.floor(Math.random() * RANDOM_NUDGES.length)];
  }

  const recent = await c.env.DB.prepare(
    `SELECT 1 FROM nudges WHERE sender = ? AND created_at > datetime('now', ?) LIMIT 1`,
  )
    .bind(c.var.username, `-${COOLDOWN_SECONDS} seconds`)
    .first();
  if (recent) return c.json({ error: 'slow down 🐧' }, 429);

  const id = crypto.randomUUID();
  await c.env.DB.prepare('INSERT INTO nudges (id, sender, message) VALUES (?, ?, ?)')
    .bind(id, c.var.username, message)
    .run();
  const row = await c.env.DB.prepare('SELECT * FROM nudges WHERE id = ?').bind(id).first<NudgeRow>();

  c.executionCtx.waitUntil(notifyPartner(c.env, c.var.username, `💌 ${c.var.username}`, message, { route: 'home' }));

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
