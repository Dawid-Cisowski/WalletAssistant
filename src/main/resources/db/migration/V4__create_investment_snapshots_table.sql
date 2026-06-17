CREATE TABLE investment_snapshots
(
    id               BIGSERIAL PRIMARY KEY,
    snapshot_id      VARCHAR(64)              NOT NULL UNIQUE,
    event_id         VARCHAR(64)              NOT NULL,
    user_id          VARCHAR(128)             NOT NULL,
    investment_type  VARCHAR(64)              NOT NULL,
    investment_name  VARCHAR(255)             NOT NULL,
    current_value    DECIMAL(12, 2)           NOT NULL,
    invested_amount  DECIMAL(12, 2),
    currency         VARCHAR(3)               NOT NULL DEFAULT 'PLN',
    recorded_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_date    DATE                     NOT NULL,
    version          BIGINT                   NOT NULL DEFAULT 1,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_investment_snapshots_user_id ON investment_snapshots (user_id);
CREATE INDEX idx_investment_snapshots_recorded_date ON investment_snapshots (recorded_date DESC);
CREATE INDEX idx_investment_snapshots_investment_type ON investment_snapshots (investment_type);
