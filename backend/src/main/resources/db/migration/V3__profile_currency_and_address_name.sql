ALTER TABLE users ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'EUR';

-- An address is now always in the name of the profile.
ALTER TABLE addresses DROP COLUMN name;
