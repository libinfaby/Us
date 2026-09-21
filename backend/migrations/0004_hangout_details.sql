-- Hangouts gain optional date-range fields and a "memories" journal, so the
-- Stash screen's hangouts tab can show what happened during a hangout, not
-- just its name and linked expenses.

ALTER TABLE hangouts ADD COLUMN start_date TEXT;
ALTER TABLE hangouts ADD COLUMN end_date TEXT;

CREATE TABLE hangout_memories (
  id TEXT PRIMARY KEY,
  hangout_id TEXT NOT NULL REFERENCES hangouts(id),
  author TEXT NOT NULL REFERENCES users(username),
  text TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_hangout_memories_hangout ON hangout_memories(hangout_id);
