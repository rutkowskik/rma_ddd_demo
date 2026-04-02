CREATE TABLE return_management.return_requests (
    id                      UUID            NOT NULL,
    version                 BIGINT          NOT NULL DEFAULT 0,
    rma_number              VARCHAR(20)     NOT NULL,
    order_id                VARCHAR(100)    NOT NULL,
    source_system           VARCHAR(30)     NOT NULL,
    customer_email          VARCHAR(255)    NOT NULL,
    customer_name           VARCHAR(255)    NOT NULL,
    status                  VARCHAR(30)     NOT NULL,
    payment_confirmed       BOOLEAN         NOT NULL DEFAULT FALSE,
    payment_session_id      VARCHAR(100),
    received_at             TIMESTAMPTZ,
    sla_deadline            DATE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_return_requests PRIMARY KEY (id),
    CONSTRAINT uq_return_requests_rma_number UNIQUE (rma_number),
    CONSTRAINT chk_return_requests_status CHECK (
        status IN ('PENDING_SHIPMENT','IN_TRANSIT','RECEIVED','VERIFICATION',
                   'DECISION','AWAITING_REFUND','REFUND_AND_DISPOSE',
                   'REJECTED','COMPLETED','BLIND_RECEIVED')
    ),
    CONSTRAINT chk_return_requests_source_system CHECK (
        source_system IN ('NEOPAK','ALLEGRO','EMAG','TEMU','BASELINKER','MANUAL')
    )
);

CREATE INDEX idx_return_requests_rma_number  ON return_management.return_requests(rma_number);
CREATE INDEX idx_return_requests_status      ON return_management.return_requests(status);
CREATE INDEX idx_return_requests_created_at  ON return_management.return_requests(created_at DESC);
CREATE INDEX idx_return_requests_order_id    ON return_management.return_requests(order_id, source_system);

COMMENT ON TABLE  return_management.return_requests IS 'Aggregate root: zgloszenie zwrotu RMA';
COMMENT ON COLUMN return_management.return_requests.version      IS 'Optimistic locking - @Version Hibernate';
COMMENT ON COLUMN return_management.return_requests.rma_number   IS 'Klucz korelacji cross-schema, format ZWR-NNNNN';
COMMENT ON COLUMN return_management.return_requests.sla_deadline IS 'Data graniczna 14-dniowego terminu zwrotu srodkow';
