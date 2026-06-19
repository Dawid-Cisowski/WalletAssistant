CREATE TABLE asset_price_history
(
    id           BIGSERIAL PRIMARY KEY,
    event_id     VARCHAR(64)              NOT NULL,
    user_id      VARCHAR(128)             NOT NULL,
    asset_symbol VARCHAR(32)              NOT NULL,
    price        DECIMAL(12, 4)           NOT NULL,
    currency     VARCHAR(3)               NOT NULL DEFAULT 'PLN',
    price_date   DATE                     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE          DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_asset_price_per_day UNIQUE (user_id, asset_symbol, price_date)
);

CREATE INDEX idx_asset_price_symbol_date ON asset_price_history (user_id, asset_symbol, price_date DESC);
