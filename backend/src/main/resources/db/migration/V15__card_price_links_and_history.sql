-- Phase 11, parts 2 and 3: the shop's own link from each Pokémon card to a Cardmarket product, Cardmarket's singles
-- with their price guide, and card prices per day. New columns and tables only: the previous version keeps working on
-- this schema.

ALTER TABLE cards ADD COLUMN cardmarket_product_id BIGINT;
ALTER TABLE cards ADD COLUMN cardmarket_link VARCHAR(20);

CREATE INDEX idx_cards_game_cardmarket_link ON cards (game, cardmarket_link);

CREATE TABLE cardmarket_singles (
    product_id         BIGINT                      NOT NULL PRIMARY KEY,
    game               VARCHAR(20)                 NOT NULL,
    name               VARCHAR(255)                NOT NULL,
    expansion_id       BIGINT                      NOT NULL,
    metacard_id        BIGINT,
    trend_cents        BIGINT,
    average_cents      BIGINT,
    foil_trend_cents   BIGINT,
    foil_average_cents BIGINT,
    updated_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_cardmarket_singles_game_expansion ON cardmarket_singles (game, expansion_id);

ALTER TABLE card_prices ADD COLUMN recorded_on DATE;
ALTER TABLE card_prices ADD COLUMN recorded_trend_cents BIGINT;
ALTER TABLE card_prices ADD COLUMN recorded_average_cents BIGINT;

-- The key starts with the card: that is also the index for "the prices of one card over time".
CREATE TABLE card_price_history (
    card_id       BIGINT      NOT NULL,
    variant       VARCHAR(30) NOT NULL,
    source        VARCHAR(20) NOT NULL,
    price_date    DATE        NOT NULL,
    currency      VARCHAR(3)  NOT NULL,
    trend_cents   BIGINT,
    average_cents BIGINT,
    PRIMARY KEY (card_id, variant, source, price_date),
    CONSTRAINT fk_card_price_history_card FOREIGN KEY (card_id) REFERENCES cards (id)
);
