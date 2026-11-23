-- Wanted cards (2026-11-23): the most a customer wants to pay for a card on their wishlist, in euro cents. Null is no
-- maximum. A new column only: the previous version keeps working on this schema.
ALTER TABLE card_wishlist_entries ADD COLUMN max_price_cents BIGINT;
