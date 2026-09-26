-- "Thinking of you" nudges: a one-tap push to the partner. Rows are kept so
-- Home can show the last nudge you received, and so the API can rate-limit
-- how fast one person fires them.

CREATE TABLE nudges (
  id TEXT PRIMARY KEY,
  sender TEXT NOT NULL REFERENCES users(username),
  message TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_nudges_sender_created ON nudges(sender, created_at);
