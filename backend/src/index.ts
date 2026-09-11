import { Hono } from 'hono';
import type { Env } from './types';
import { authRoutes } from './routes/auth';

const app = new Hono<{ Bindings: Env }>();

app.get('/', (c) => c.json({ ok: true, service: 'pingu-codu-api' }));
app.route('/auth', authRoutes);

export default app;
