CREATE TABLE return_management.shipments (
    id                  UUID            NOT NULL,
    return_request_id   UUID            NOT NULL,
    weight_kg           INTEGER         NOT NULL,
    length_cm           INTEGER         NOT NULL,
    width_cm            INTEGER         NOT NULL,
    height_cm           INTEGER         NOT NULL,
    label_url           VARCHAR(500),
    tracking_number     VARCHAR(100),
    received            BOOLEAN         NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_shipments PRIMARY KEY (id),
    CONSTRAINT fk_shipments_return_request
        FOREIGN KEY (return_request_id)
        REFERENCES return_management.return_requests(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_shipments_weight CHECK (weight_kg > 0)
);

CREATE INDEX idx_shipments_return_request_id ON return_management.shipments(return_request_id);
CREATE INDEX idx_shipments_tracking_number   ON return_management.shipments(tracking_number)
    WHERE tracking_number IS NOT NULL;

COMMENT ON TABLE  return_management.shipments IS 'Paczki fizyczne w ramach jednego zgloszenia (multi-package)';
COMMENT ON COLUMN return_management.shipments.tracking_number IS 'Numer listu kuriera — null przed nadaniem';
