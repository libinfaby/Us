-- Movies and places can carry the link they were saved from (IMDb,
-- Letterboxd, Google Maps...), so the card can open it again. The title and
-- description the link preview fetched live in the existing title/body.

ALTER TABLE stash_items ADD COLUMN url TEXT;
