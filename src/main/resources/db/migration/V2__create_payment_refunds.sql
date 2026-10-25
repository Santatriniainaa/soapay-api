CREATE TABLE payment_refunds (
    id UUID PRIMARY KEY,
    refund_reference VARCHAR(32) NOT NULL UNIQUE,
    idempotency_key VARCHAR(128) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    transaction_reference VARCHAR(32) NOT NULL REFERENCES payment_transactions(transaction_reference),
    amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_refund_client_idempotency UNIQUE (client_id, idempotency_key)
);

CREATE INDEX idx_refund_payment ON payment_refunds (transaction_reference, created_at DESC);
