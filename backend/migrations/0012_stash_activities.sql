-- Stash gets an "activity" type: things we want to do together some day. SQLite
-- can't change a CHECK constraint in place, so the table is rebuilt with the new
-- list of types and every row copied across, as in 0010. Nothing references
-- stash_items, so dropping the old table is safe.

CREATE TABLE stash_items_new (
  id TEXT PRIMARY KEY,
  type TEXT NOT NULL CHECK (type IN ('movie', 'book', 'activity', 'link', 'place', 'note', 'todo')),
  author TEXT NOT NULL REFERENCES users(username),
  title TEXT NOT NULL,
  body TEXT,
  url TEXT,
  tags_json TEXT NOT NULL DEFAULT '[]',
  status TEXT NOT NULL DEFAULT 'saved' CHECK (status IN ('saved', 'done')),
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  hangout_id TEXT REFERENCES hangouts(id)
);

INSERT INTO stash_items_new (id, type, author, title, body, url, tags_json, status, created_at, updated_at, hangout_id)
SELECT id, type, author, title, body, url, tags_json, status, created_at, updated_at, hangout_id FROM stash_items;

DROP TABLE stash_items;
ALTER TABLE stash_items_new RENAME TO stash_items;

CREATE INDEX idx_stash_type ON stash_items(type);
CREATE INDEX idx_stash_hangout ON stash_items(hangout_id);
