-- Every shop checkout counts the open checkouts of a guest by email key and status (phase 9 final review, R8), and
-- drop limits look orders up by email key too. A new index only: the previous version keeps working on this schema.

CREATE INDEX idx_orders_email_key_status ON orders (email_key, status);
