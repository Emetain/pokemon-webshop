-- Indexes for foreign keys that had none (phase 9 audit, N5). A unique constraint only helps a lookup on its first
-- column, so a card or user in second place still needed one of its own. Deleting a card, user or product checks
-- these tables, and some lists filter on them.

CREATE INDEX idx_binder_selections_card_id ON binder_selections (card_id);
CREATE INDEX idx_card_wishlist_entries_card_id ON card_wishlist_entries (card_id);
CREATE INDEX idx_drop_queue_entries_user_id ON drop_queue_entries (user_id);
CREATE INDEX idx_product_reviews_user_id ON product_reviews (user_id);
CREATE INDEX idx_set_release_products_product_id ON set_release_products (product_id);
CREATE INDEX idx_set_releases_set_id ON set_releases (set_id);
