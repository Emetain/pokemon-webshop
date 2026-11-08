-- Whether a set is still printed and its place among the most popular sets. Both follow the settings in
-- app.shop and are filled in at startup, so existing sets start as in print and without a place.
ALTER TABLE card_sets ADD COLUMN in_print BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE card_sets ADD COLUMN popular_rank INTEGER;
