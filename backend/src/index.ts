import { Hono } from 'hono';
import type { Env } from './types';
import { authRoutes } from './routes/auth';
import { expensesRoutes } from './routes/expenses';
import { hangoutsRoutes } from './routes/hangouts';
import { cycleRoutes } from './routes/cycle';
import { stashRoutes } from './routes/stash';
import { devicesRoutes } from './routes/devices';
import { nudgesRoutes } from './routes/nudges';
import { datesRoutes } from './routes/dates';
import { runDailyReminders } from './lib/reminders';

const app = new Hono<{ Bindings: Env }>();

app.get('/', (c) => c.json({ ok: true, service: 'pingu-codu-api' }));
app.route('/auth', authRoutes);
app.route('/expenses', expensesRoutes);
app.route('/hangouts', hangoutsRoutes);
app.route('/cycle', cycleRoutes);
app.route('/stash', stashRoutes);
app.route('/devices', devicesRoutes);
app.route('/nudges', nudgesRoutes);
app.route('/dates', datesRoutes);

export default {
  fetch: app.fetch,
  // Daily cron (see "triggers" in wrangler.jsonc) - countdown and anniversary reminders.
  scheduled(_controller, env, ctx) {
    ctx.waitUntil(runDailyReminders(env));
  },
} satisfies ExportedHandler<Env>;
