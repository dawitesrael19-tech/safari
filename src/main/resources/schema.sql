SELECT pg_advisory_lock(424242);

CREATE TABLE IF NOT EXISTS wallets (
    wallet_id   VARCHAR(16) PRIMARY KEY,
    status      VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN')),
    merchant    BOOLEAN     NOT NULL DEFAULT FALSE,
    balance     BIGINT      NOT NULL CHECK (balance >= 0),
    reserved    BIGINT      NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (reserved <= balance)
);

CREATE SEQUENCE IF NOT EXISTS transfer_seq START 10000;
CREATE SEQUENCE IF NOT EXISTS reversal_seq START 500;

CREATE TABLE IF NOT EXISTS transfers (
    transfer_id     VARCHAR(32)  PRIMARY KEY,
    from_wallet     VARCHAR(16)  NOT NULL,
    to_wallet       VARCHAR(16)  NOT NULL,
    amount          BIGINT       NOT NULL CHECK (amount > 0),
    reference       VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('COMPLETED', 'REVERSED')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reversal_id     VARCHAR(32),
    reversal_reason VARCHAR(255),
    reversed_at     TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS payouts (
    payout_id       VARCHAR(32)  PRIMARY KEY,
    wallet_id       VARCHAR(16)  NOT NULL,
    bank_account    VARCHAR(64)  NOT NULL,
    amount          BIGINT       NOT NULL CHECK (amount > 0),
    state           VARCHAR(16)  NOT NULL CHECK (state IN ('NEW', 'SUBMITTED', 'UNKNOWN', 'SUCCEEDED', 'FAILED')),
    bank_ref        VARCHAR(64),
    conflict        BOOLEAN      NOT NULL DEFAULT FALSE,
    next_attempt_at TIMESTAMPTZ,
    lease_until     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS payouts_due ON payouts (next_attempt_at) WHERE state IN ('NEW', 'SUBMITTED', 'UNKNOWN');

CREATE TABLE IF NOT EXISTS idempotency_keys (
    idem_key        VARCHAR(64)  PRIMARY KEY,
    request_hash    VARCHAR(64)  NOT NULL,
    response_status INT,
    response_body   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

SELECT pg_advisory_unlock(424242);
