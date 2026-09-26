-- Dates we care about. A 'countdown' is a future day we're looking forward to
-- (trip, concert); a 'milestone' is a past day we celebrate every year (first
-- date, first trip). Both are reminded about by the daily cron in
-- src/lib/reminders.ts.

CREATE TABLE special_dates (
  id TEXT PRIMARY KEY,
  kind TEXT NOT NULL CHECK (kind IN ('countdown', 'milestone')),
  title TEXT NOT NULL,
  emoji TEXT,
  date TEXT NOT NULL,
  created_by TEXT NOT NULL REFERENCES users(username),
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
