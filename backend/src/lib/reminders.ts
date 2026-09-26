import type { Env } from '../types';
import { notifyBoth } from './notify';
import { daysBetween, nextAnniversary, todayIst } from './dates';

type SpecialDateRow = { id: string; kind: 'countdown' | 'milestone'; title: string; emoji: string | null; date: string };

/**
 * Runs once a day from the cron trigger in wrangler.jsonc (08:00 IST) and
 * pushes both of us about countdowns hitting tomorrow/today and milestone
 * anniversaries falling today. Each row is isolated so one bad row can't
 * stop the rest from being sent.
 */
export async function runDailyReminders(env: Env): Promise<void> {
  const today = todayIst();
  const { results } = await env.DB.prepare('SELECT * FROM special_dates').all<SpecialDateRow>();

  const sends: Promise<void>[] = [];
  for (const row of results) {
    try {
      const label = row.title;
      if (row.kind === 'countdown') {
        const daysUntil = daysBetween(today, row.date);
        if (daysUntil === 1) {
          sends.push(notifyBoth(env, 'Tomorrow!', `tomorrow: ${label}!`, { route: 'dates' }));
        } else if (daysUntil === 0) {
          sends.push(notifyBoth(env, "Today's the day", `today's the day: ${label}`, { route: 'dates' }));
        }
      } else {
        const next = nextAnniversary(row.date, today);
        if (next.date === today && next.years >= 1) {
          const years = next.years === 1 ? '1 year' : `${next.years} years`;
          sends.push(notifyBoth(env, 'Happy anniversary', `${years} since ${label}`, { route: 'dates' }));
        }
      }
    } catch (err) {
      console.error(`runDailyReminders: failed on special_date ${row.id}`, err);
    }
  }
  await Promise.all(sends);
}
