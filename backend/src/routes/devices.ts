import { Hono } from 'hono';
import type { Env } from '../types';
import { requireAuth, type AuthVariables } from '../middleware/auth';

export const devicesRoutes = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

devicesRoutes.use('*', requireAuth);

devicesRoutes.post('/', async (c) => {
  const body = await c.req.json().catch(() => null);
  const fcmToken = body?.fcmToken;
  if (typeof fcmToken !== 'string' || fcmToken.trim().length === 0) {
    return c.json({ error: 'fcmToken is required' }, 400);
  }

  await c.env.DB.prepare(
    `INSERT INTO device_tokens (username, fcm_token) VALUES (?, ?)
     ON CONFLICT(username) DO UPDATE SET fcm_token = excluded.fcm_token, updated_at = datetime('now')`,
  )
    .bind(c.var.username, fcmToken.trim())
    .run();

  return c.json({ ok: true });
});
