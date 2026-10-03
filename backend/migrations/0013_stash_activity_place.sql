-- An activity can be tied to a place from the stash (e.g. "scuba diving" at a
-- beach we saved), and a place lists the activities tied to it. Like hangout_id,
-- there's no foreign key: deleting a place unlinks its activities in the route.

ALTER TABLE stash_items ADD COLUMN place_id TEXT;

CREATE INDEX idx_stash_place ON stash_items(place_id);
