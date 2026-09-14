import { Hono } from 'hono';
import type { Env } from './types';
import { authRoutes } from './routes/auth';
import { expensesRoutes } from './routes/expenses';
import { hangoutsRoutes } from './routes/hangouts';
import { cycleRoutes } from './routes/cycle';
import { stashRoutes } from './routes/stash';

const app = new Hono<{ Bindings: Env }>();

app.get('/', (c) => c.json({ ok: true, service: 'pingu-codu-api' }));
app.route('/auth', authRoutes);
app.route('/expenses', expensesRoutes);
app.route('/hangouts', hangoutsRoutes);
app.route('/cycle', cycleRoutes);
app.route('/stash', stashRoutes);

export default app;
