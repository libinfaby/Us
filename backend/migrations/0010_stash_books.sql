-- Stash gets a "book" type (details and link from Google Books). SQLite can't
-- change a CHECK constraint in place, so the table is rebuilt with the new
-- list of types and every row copied across. Nothing references stash_items,
-- so dropping the old table is safe.

CREATE TABLE stash_items_new (
  id TEXT PRIMARY KEY,
  type TEXT NOT NULL CHECK (type IN ('movie', 'book', 'link', 'place', 'note', 'todo')),
  author TEXT NOT NULL REFERENCES users(username),
  title TEXT NOT NULL,
  body TEXT,
  url TEXT,
  tags_json TEXT NOT NULL DEFAULT '[]',
  status TEXT NOT NULL DEFAULT 'saved' CHECK (status IN ('saved', 'done')),
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT INTO stash_items_new (id, type, author, title, body, url, tags_json, status, created_at, updated_at)
SELECT id, type, author, title, body, url, tags_json, status, created_at, updated_at FROM stash_items;

DROP TABLE stash_items;
ALTER TABLE stash_items_new RENAME TO stash_items;

CREATE INDEX idx_stash_type ON stash_items(type);
