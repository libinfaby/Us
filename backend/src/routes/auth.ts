import { Hono } from 'hono';
import type { Env } from '../types';
import { hashPin, signSession, verifyPin } from '../lib/crypto';
import { isUsername } from '../lib/users';
import { requireAuth, type AuthVariables } from '../middleware/auth';

export const authRoutes = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

/**
 * One-time bootstrap: sets a user's PIN. Self-disables once a PIN is already
 * set, so this never needs to appear in git or be locked down separately -
 * it's only ever exploitable during the brief window before you've run it.
 */
authRoutes.post('/set-pin', async (c) => {
  const body = await c.req.json().catch(() => null);
  const { username, pin } = body ?? {};
  if (!isUsername(username) || typeof pin !== 'string' || pin.length < 4) {
    return c.json({ error: 'username must be pingu/codu, pin must be at least 4 characters' }, 400);
  }

  const existing = await c.env.DB.prepare('SELECT pin_hash FROM users WHERE username = ?')
    .bind(username)
    .first<{ pin_hash: string | null }>();
  if (!existing) return c.json({ error: 'unknown user' }, 404);
  if (existing.pin_hash) return c.json({ error: 'pin already set for this user' }, 403);

  const pinHash = await hashPin(pin);
  await c.env.DB.prepare('UPDATE users SET pin_hash = ? WHERE username = ?').bind(pinHash, username).run();
  return c.json({ ok: true });
});

authRoutes.post('/login', async (c) => {
  const body = await c.req.json().catch(() => null);
  const { username, pin } = body ?? {};
  if (!isUsername(username) || typeof pin !== 'string') {
    return c.json({ error: 'username must be pingu/codu, pin is required' }, 400);
  }

  const user = await c.env.DB.prepare('SELECT pin_hash FROM users WHERE username = ?')
    .bind(username)
    .first<{ pin_hash: string | null }>();
  if (!user?.pin_hash) return c.json({ error: 'pin not set up yet - call /auth/set-pin first' }, 400);

  const valid = await verifyPin(pin, user.pin_hash);
  if (!valid) return c.json({ error: 'wrong pin' }, 401);

  const token = await signSession({ u: username, iat: Date.now() }, c.env.SESSION_SECRET);
  return c.json({ token, username });
});

/**
 * Requires both a valid session (you're logged in as this user) and the
 * current pin (in case a device with a long-lived session is lost/stolen).
 */
authRoutes.post('/change-pin', requireAuth, async (c) => {
  const username = c.var.username;
  const body = await c.req.json().catch(() => null);
  const { currentPin, newPin } = body ?? {};
  if (typeof currentPin !== 'string' || typeof newPin !== 'string' || newPin.length < 4) {
    return c.json({ error: 'currentPin and newPin (min 4 characters) are required' }, 400);
  }

  const user = await c.env.DB.prepare('SELECT pin_hash FROM users WHERE username = ?')
    .bind(username)
    .first<{ pin_hash: string | null }>();
  if (!user?.pin_hash || !(await verifyPin(currentPin, user.pin_hash))) {
    return c.json({ error: 'current pin is incorrect' }, 401);
  }

  const newHash = await hashPin(newPin);
  await c.env.DB.prepare('UPDATE users SET pin_hash = ? WHERE username = ?').bind(newHash, username).run();
  return c.json({ ok: true });
});
