ALTER TABLE asset_price_history
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
