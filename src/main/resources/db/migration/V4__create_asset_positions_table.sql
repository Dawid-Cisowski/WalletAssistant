CREATE TABLE asset_positions
(
    id             BIGSERIAL PRIMARY KEY,
    position_id    VARCHAR(64)                  UNIQUE NOT NULL,
    event_id       VARCHAR(64)                  NOT NULL,
    user_id        VARCHAR(128)                 NOT NULL,
    portfolio_type VARCHAR(32)                  NOT NULL,
    asset_symbol   VARCHAR(32)                  NOT NULL,
    asset_type     VARCHAR(32)                  NOT NULL,
    asset_name     VARCHAR(255)                 NOT NULL,
    quantity       DECIMAL(20, 8)               NOT NULL,
    purchase_price DECIMAL(12, 4)               NOT NULL,
    purchased_at   DATE                         NOT NULL,
    currency       VARCHAR(3)                   NOT NULL DEFAULT 'PLN',
    status         VARCHAR(16)                  NOT NULL DEFAULT 'OPEN',
    sale_price     DECIMAL(12, 4),
    sold_at        DATE,
    version        BIGINT                       NOT NULL DEFAULT 1,
    created_at     TIMESTAMP WITH TIME ZONE              DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE              DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_asset_positions_user_id   ON asset_positions (user_id);
CREATE INDEX idx_asset_positions_symbol    ON asset_positions (user_id, asset_symbol);
CREATE INDEX idx_asset_positions_portfolio ON asset_positions (user_id, portfolio_type);
CREATE INDEX idx_asset_positions_status    ON asset_positions (user_id, status);
