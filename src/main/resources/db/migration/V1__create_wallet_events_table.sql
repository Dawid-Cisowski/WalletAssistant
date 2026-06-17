CREATE TABLE wallet_events
(
    id                      BIGSERIAL PRIMARY KEY,
    event_id                VARCHAR(64)              NOT NULL UNIQUE,
    idempotency_key         VARCHAR(512)             NOT NULL UNIQUE,
    event_type              VARCHAR(64)              NOT NULL,
    occurred_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    payload                 JSONB                    NOT NULL,
    user_id                 VARCHAR(128)             NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by_event_id     VARCHAR(64),
    superseded_by_event_id  VARCHAR(64),
    version                 BIGINT                   NOT NULL DEFAULT 1
);

CREATE INDEX idx_wallet_events_user_id ON wallet_events (user_id);
CREATE INDEX idx_wallet_events_event_type ON wallet_events (event_type);
CREATE INDEX idx_wallet_events_occurred_at ON wallet_events (occurred_at DESC);
CREATE INDEX idx_wallet_events_payload ON wallet_events USING GIN (payload);
