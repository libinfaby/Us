import { createMiddleware } from 'hono/factory';
import type { Env } from '../types';
import { verifySession } from '../lib/crypto';
import type { Username } from '../lib/users';

export type AuthVariables = { username: Username };

/** Verifies the Bearer session token and makes the acting username available via c.var.username. */
export const requireAuth = createMiddleware<{ Bindings: Env; Variables: AuthVariables }>(async (c, next) => {
  const authHeader = c.req.header('Authorization') ?? '';
  const token = authHeader.startsWith('Bearer ') ? authHeader.slice('Bearer '.length) : null;
  if (!token) return c.json({ error: 'missing bearer token' }, 401);

  const session = await verifySession(token, c.env.SESSION_SECRET);
  if (!session) return c.json({ error: 'invalid or expired session' }, 401);

  c.set('username', session.u);
  await next();
});
