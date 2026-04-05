-- Support tickets
CREATE TABLE support_tickets (
    id                    BIGSERIAL PRIMARY KEY,
    order_id              BIGINT         NOT NULL REFERENCES orders(id),
    customer_id           BIGINT         NOT NULL REFERENCES users(id),
    assigned_to_id        BIGINT         REFERENCES users(id),
    type                  VARCHAR(30)    NOT NULL CHECK (type IN ('REFUND_ONLY','RETURN_AND_REFUND')),
    status                VARCHAR(30)    NOT NULL CHECK (status IN (
        'OPEN','AWAITING_EVIDENCE','UNDER_REVIEW','APPROVED','REJECTED',
        'REFUNDED','RETURN_SHIPPED','RETURN_RECEIVED','CLOSED','ESCALATED')),
    description           TEXT           NOT NULL,
    refund_amount         NUMERIC(10,2),
    auto_approved         BOOLEAN        NOT NULL DEFAULT FALSE,
    first_response_at     TIMESTAMPTZ,
    first_response_due_at TIMESTAMPTZ,
    sla_breached          BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    retention_expires_at  TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_ticket_order    ON support_tickets(order_id);
CREATE INDEX idx_ticket_customer ON support_tickets(customer_id);
CREATE INDEX idx_ticket_status   ON support_tickets(status);
CREATE INDEX idx_ticket_sla      ON support_tickets(first_response_due_at) WHERE first_response_at IS NULL;
CREATE INDEX idx_ticket_retention ON support_tickets(retention_expires_at);

-- Evidence files with SHA-256 tamper detection
CREATE TABLE evidence_files (
    id             BIGSERIAL PRIMARY KEY,
    ticket_id      BIGINT       NOT NULL REFERENCES support_tickets(id),
    uploaded_by_id BIGINT       NOT NULL REFERENCES users(id),
    file_name      VARCHAR(255) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    file_size      BIGINT       NOT NULL,
    storage_path   VARCHAR(500) NOT NULL,
    sha256_hash    VARCHAR(64)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_evidence_ticket ON evidence_files(ticket_id);
