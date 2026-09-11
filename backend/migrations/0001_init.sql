-- Core schema for pingu & codu.
-- Only ever 2 users, so `username` doubles as the primary key - no need for
-- generated ids there. pin_hash starts NULL and is set once via POST
-- /auth/set-pin (see src/routes/auth.ts) - never committed to git.

CREATE TABLE users (
  username TEXT PRIMARY KEY CHECK (username IN ('pingu', 'codu')),
  pin_hash TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT INTO users (username) VALUES ('pingu'), ('codu');

CREATE TABLE hangouts (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE expenses (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  subtitle TEXT,
  amount_cents INTEGER NOT NULL,
  currency TEXT NOT NULL DEFAULT 'INR',
  expense_date TEXT NOT NULL,
  location TEXT,
  is_recurring INTEGER NOT NULL DEFAULT 0,
  cadence TEXT,
  hangout_id TEXT REFERENCES hangouts(id),
  category TEXT,
  paid_by TEXT NOT NULL REFERENCES users(username),
  split_type TEXT NOT NULL DEFAULT 'equal' CHECK (split_type IN ('equal', 'custom')),
  split_json TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'settled')),
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_expenses_status ON expenses(status);
CREATE INDEX idx_expenses_hangout ON expenses(hangout_id);

CREATE TABLE stash_items (
  id TEXT PRIMARY KEY,
  type TEXT NOT NULL CHECK (type IN ('movie', 'link', 'place', 'note', 'todo')),
  author TEXT NOT NULL REFERENCES users(username),
  title TEXT NOT NULL,
  body TEXT,
  tags_json TEXT NOT NULL DEFAULT '[]',
  status TEXT NOT NULL DEFAULT 'saved' CHECK (status IN ('saved', 'done')),
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_stash_type ON stash_items(type);

-- Belongs implicitly to whichever user is `cycle_settings.tracked_user` - only
-- that user's client ever calls the endpoints that read/write this table.
CREATE TABLE cycle_logs (
  id TEXT PRIMARY KEY,
  log_date TEXT NOT NULL UNIQUE,
  flow TEXT CHECK (flow IN ('spotting', 'light', 'medium', 'heavy')),
  symptoms_json TEXT NOT NULL DEFAULT '[]',
  note TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE cycle_settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  tracked_user TEXT NOT NULL REFERENCES users(username),
  avg_cycle_length INTEGER NOT NULL DEFAULT 28,
  avg_period_length INTEGER NOT NULL DEFAULT 5
);

INSERT INTO cycle_settings (id, tracked_user) VALUES (1, 'pingu');
