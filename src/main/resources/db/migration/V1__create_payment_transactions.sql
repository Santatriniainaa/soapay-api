CREATE TABLE api_transactions (
    id UUID PRIMARY KEY,
    transaction_reference VARCHAR(32) NOT NULL UNIQUE,
    idempotency_key VARCHAR(128) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    phone_number VARCHAR(16) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    description VARCHAR(255),
    failure_code VARCHAR(64),
    failure_message VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_api_client_idempotency UNIQUE (client_id, idempotency_key)
);

CREATE INDEX idx_api_client_created ON api_transactions (client_id, created_at DESC);
