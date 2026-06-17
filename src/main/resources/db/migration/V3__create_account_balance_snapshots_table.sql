CREATE TABLE account_balance_snapshots
(
    id            BIGSERIAL PRIMARY KEY,
    snapshot_id   VARCHAR(64)              NOT NULL UNIQUE,
    event_id      VARCHAR(64)              NOT NULL,
    user_id       VARCHAR(128)             NOT NULL,
    account_type  VARCHAR(64)              NOT NULL,
    account_name  VARCHAR(255)             NOT NULL,
    balance       DECIMAL(12, 2)           NOT NULL,
    currency      VARCHAR(3)               NOT NULL DEFAULT 'PLN',
    recorded_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_date DATE                     NOT NULL,
    version       BIGINT                   NOT NULL DEFAULT 1,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_account_balance_snapshots_user_id ON account_balance_snapshots (user_id);
CREATE INDEX idx_account_balance_snapshots_recorded_date ON account_balance_snapshots (recorded_date DESC);
CREATE INDEX idx_account_balance_snapshots_account_type ON account_balance_snapshots (account_type);
