CREATE TABLE customers
(
    customer_id VARCHAR(10) PRIMARY KEY,
    name         VARCHAR(100) NOT NULL
);
INSERT INTO customers (customer_id, name)
VALUES ('C001', 'Kim'),
       ('C002', 'Lee'),
       ('C003', 'Park');

CREATE TABLE transfers
(
    transfer_id      VARCHAR(36)              NOT NULL,
    customer_id      VARCHAR(10)              NOT NULL,
    idempotency_key  VARCHAR(64)              NOT NULL,
    request_hash     CHAR(64)                 NOT NULL,
    send_currency    CHAR(3)                  NOT NULL,
    send_amount      BIGINT                   NOT NULL,
    fee              BIGINT                   NOT NULL,
    total_debit      BIGINT                   NOT NULL,
    fx_rate          DECIMAL(10, 4)           NOT NULL,
    receive_currency CHAR(3)                  NOT NULL,
    receive_amount   DECIMAL(19, 2)           NOT NULL,
    recipient_name   VARCHAR(100)              NOT NULL,
    status           VARCHAR(16)              NOT NULL,
    partner_ref      VARCHAR(64),
    failure_reason   VARCHAR(255),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_transfers PRIMARY KEY (transfer_id),

    -- transfer harus berasal dari nasabah yang nyata
    CONSTRAINT fk_transfers_customer
        FOREIGN KEY (customer_id) REFERENCES customers (customer_id),

    -- satu kunci idempotency = satu transfer, bahkan ketika dua upaya ulang tiba saat yang bersamaan
    CONSTRAINT uq_transfers_idempotency_key UNIQUE (idempotency_key),

    -- satu referensi mitra = satu transfer
    CONSTRAINT uq_transfers_partner_ref UNIQUE (partner_ref),

    -- hanya berstatus ini aja yang dapat di simpan
    CONSTRAINT ck_transfers_status
        CHECK ( status IN ('REQUESTED', 'SENDING', 'COMPLETED', 'FAILED', 'CANCELLED')),

    -- Transfer yang sudah dikirim ke partner, selalu menyimpan (tidak boleh kehilangan) referensi partner-nya.
    CONSTRAINT ck_transfers_partner_ref
        CHECK ( status IN ('REQUESTED', 'CANCELLED') OR partner_ref IS NOT NULL ),

    CONSTRAINT ck_transfers_send_amount CHECK ( send_amount > 0 ),
    CONSTRAINT ck_transfers_fee CHECK ( fee >= 0 ),
    CONSTRAINT ck_transfers_total_debit CHECK ( total_debit = send_amount + fee ),
    CONSTRAINT ck_transfers_fx_rate CHECK ( fx_rate > 0 ),
    CONSTRAINT ck_transfers_receive_amount CHECK ( receive_amount > 0 )
);

CREATE INDEX idx_transfers_customers_created ON transfers (customer_id, created_at);

