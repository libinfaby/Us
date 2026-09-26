-- Shared savings goals ("Goa fund: ₹18k / ₹50k"). Contributions are money
-- each of us sets aside towards a goal - they're separate from expenses and
-- never affect the /expenses/balance score. A negative amount is a
-- withdrawal.

CREATE TABLE savings_goals (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  emoji TEXT,
  target_cents INTEGER NOT NULL CHECK (target_cents > 0),
  target_date TEXT,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'reached', 'archived')),
  created_by TEXT NOT NULL REFERENCES users(username),
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE goal_contributions (
  id TEXT PRIMARY KEY,
  goal_id TEXT NOT NULL REFERENCES savings_goals(id),
  username TEXT NOT NULL REFERENCES users(username),
  amount_cents INTEGER NOT NULL CHECK (amount_cents != 0),
  note TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_goal_contributions_goal ON goal_contributions(goal_id);
