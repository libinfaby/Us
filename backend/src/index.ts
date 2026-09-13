import { Hono } from 'hono';
import type { Env } from './types';
import { authRoutes } from './routes/auth';
import { expensesRoutes } from './routes/expenses';
import { hangoutsRoutes } from './routes/hangouts';

const app = new Hono<{ Bindings: Env }>();

app.get('/', (c) => c.json({ ok: true, service: 'pingu-codu-api' }));
app.route('/auth', authRoutes);
app.route('/expenses', expensesRoutes);
app.route('/hangouts', hangoutsRoutes);

export default app;
