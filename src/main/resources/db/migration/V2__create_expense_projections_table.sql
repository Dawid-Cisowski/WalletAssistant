CREATE TABLE expense_projections
(
    id            BIGSERIAL PRIMARY KEY,
    expense_id    VARCHAR(64)              NOT NULL UNIQUE,
    event_id      VARCHAR(64)              NOT NULL,
    user_id       VARCHAR(128)             NOT NULL,
    amount        DECIMAL(12, 2)           NOT NULL,
    currency      VARCHAR(3)               NOT NULL DEFAULT 'PLN',
    category      VARCHAR(64)              NOT NULL,
    description   VARCHAR(500),
    merchant      VARCHAR(255),
    account_type  VARCHAR(64)              NOT NULL,
    occurred_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    occurred_date DATE                     NOT NULL,
    version       BIGINT                   NOT NULL DEFAULT 1,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_expense_projections_user_id ON expense_projections (user_id);
CREATE INDEX idx_expense_projections_occurred_date ON expense_projections (occurred_date DESC);
CREATE INDEX idx_expense_projections_category ON expense_projections (category);
CREATE INDEX idx_expense_projections_account_type ON expense_projections (account_type);
