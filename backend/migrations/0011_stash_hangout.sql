-- A stash item can belong to a hangout (the place we went, the movie we
-- watched on the trip), the same way an expense can. Deleting a hangout
-- unlinks its items rather than deleting them.

ALTER TABLE stash_items ADD COLUMN hangout_id TEXT REFERENCES hangouts(id);

CREATE INDEX idx_stash_hangout ON stash_items(hangout_id);
