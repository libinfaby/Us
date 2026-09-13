import { Hono } from 'hono';
import type { Env } from '../types';
import { requireAuth, type AuthVariables } from '../middleware/auth';

export const hangoutsRoutes = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

hangoutsRoutes.use('*', requireAuth);

type HangoutRow = { id: string; name: string; created_at: string };

function toHangoutJson(row: HangoutRow) {
  return { id: row.id, name: row.name, createdAt: row.created_at };
}

hangoutsRoutes.get('/', async (c) => {
  const { results } = await c.env.DB.prepare('SELECT * FROM hangouts ORDER BY created_at DESC').all<HangoutRow>();
  return c.json(results.map(toHangoutJson));
});

hangoutsRoutes.post('/', async (c) => {
  const body = await c.req.json().catch(() => null);
  const { name } = body ?? {};
  if (typeof name !== 'string' || name.trim().length === 0) {
    return c.json({ error: 'name is required' }, 400);
  }

  const id = crypto.randomUUID();
  await c.env.DB.prepare('INSERT INTO hangouts (id, name) VALUES (?, ?)').bind(id, name.trim()).run();
  const row = await c.env.DB.prepare('SELECT * FROM hangouts WHERE id = ?').bind(id).first<HangoutRow>();
  return c.json(toHangoutJson(row!), 201);
});
