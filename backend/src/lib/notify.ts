import { USERNAMES, type Username } from './users';
import { sendFcmMessage } from './fcm';
import type { Env } from '../types';

// Pictographs plus the joiners, variation selectors, skin tones, flag letters and
// keycap marks that combine with them into a single emoji.
const EMOJI = /[\p{Extended_Pictographic}\u{1F1E6}-\u{1F1FF}\u{1F3FB}-\u{1F3FF}‍︎️⃣]/gu;

/** Push text has no emojis, and double quotes become single quotes. */
function cleanPushText(text: string): string {
  return text
    .replace(EMOJI, '')
    .replace(/["“”]/g, "'")
    .replace(/\s{2,}/g, ' ')
    .trim();
}

/**
 * Fire-and-forget: looks up [target]'s registered device and pushes to it.
 * Never throws - a push failure must never break the request that triggered
 * it. Call sites should invoke this via c.executionCtx.waitUntil so the
 * response doesn't wait on the JWT sign + outbound HTTP calls.
 */
export async function notifyUser(
  env: Env,
  target: Username,
  title: string,
  body: string,
  data: Record<string, string>,
): Promise<void> {
  try {
    const row = await env.DB.prepare('SELECT fcm_token FROM device_tokens WHERE username = ?')
      .bind(target)
      .first<{ fcm_token: string }>();
    if (!row) return;

    const result = await sendFcmMessage(env.FCM_SERVICE_ACCOUNT_JSON, row.fcm_token, cleanPushText(title), cleanPushText(body), data);
    if (!result.ok && result.invalidToken) {
      await env.DB.prepare('DELETE FROM device_tokens WHERE username = ?').bind(target).run();
    } else if (!result.ok) {
      console.error(`notifyUser: FCM send failed with status ${result.status}`);
    }
  } catch (err) {
    console.error('notifyUser failed', err);
  }
}

/** Pushes to the *other* user - the one who didn't perform [actor]'s action. */
export async function notifyPartner(
  env: Env,
  actor: Username,
  title: string,
  body: string,
  data: Record<string, string>,
): Promise<void> {
  const target = USERNAMES.find((u) => u !== actor)!;
  await notifyUser(env, target, title, body, data);
}

/** Pushes to both of us - for events with no actor, like the daily reminder cron. */
export async function notifyBoth(env: Env, title: string, body: string, data: Record<string, string>): Promise<void> {
  await Promise.all(USERNAMES.map((u) => notifyUser(env, u, title, body, data)));
}
