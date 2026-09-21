-- Reverse direction of cycle_logs: written by whichever user is NOT
-- cycle_settings.tracked_user, about the tracked user's behavior that day.
CREATE TABLE partner_observations (
  id TEXT PRIMARY KEY,
  obs_date TEXT NOT NULL UNIQUE,
  tags_json TEXT NOT NULL DEFAULT '[]',
  note TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
