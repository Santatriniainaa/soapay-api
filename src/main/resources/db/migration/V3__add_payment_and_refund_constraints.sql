ALTER TABLE api_transactions
    ADD CONSTRAINT ck_api_transaction_provider
        CHECK (provider IN ('MVOLA_MOCK', 'ORANGE_MONEY_MOCK', 'AIRTEL_MONEY_MOCK'));

ALTER TABLE api_transactions
    ADD CONSTRAINT ck_api_transaction_status
        CHECK (status IN ('CREATED', 'PENDING', 'SUCCESS', 'FAILED', 'CANCELLED',
                          'REFUND_PENDING', 'REFUNDED', 'PARTIALLY_REFUNDED'));

ALTER TABLE api_transactions
    ADD CONSTRAINT ck_api_transaction_currency
        CHECK (CHAR_LENGTH(currency) = 3 AND currency = UPPER(currency));

ALTER TABLE api_refunds
    ADD CONSTRAINT ck_api_refund_status
        CHECK (status IN ('PENDING', 'REFUNDED', 'FAILED'));

ALTER TABLE api_refunds
    ADD CONSTRAINT ck_api_refund_processed_at
        CHECK ((status = 'PENDING' AND processed_at IS NULL)
            OR (status IN ('REFUNDED', 'FAILED') AND processed_at IS NOT NULL));