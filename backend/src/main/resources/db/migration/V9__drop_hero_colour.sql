-- The background colour of the hero slide of a drop. Existing drops start black.
ALTER TABLE drops ADD COLUMN hero_colour VARCHAR(20) DEFAULT 'BLACK' NOT NULL;
