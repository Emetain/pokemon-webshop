-- Makes "name contains" searches fast. Lives here because this index only exists on PostgreSQL.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_cards_name_trigram ON cards USING gin (name_search gin_trgm_ops);
