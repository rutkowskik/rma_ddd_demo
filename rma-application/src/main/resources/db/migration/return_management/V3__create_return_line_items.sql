CREATE TABLE return_management.return_line_items (
    id                   UUID            NOT NULL,
    return_request_id    UUID            NOT NULL,
    product_id           VARCHAR(100)    NOT NULL,
    quantity             INTEGER         NOT NULL,
    return_reason        VARCHAR(30)     NOT NULL,
    condition_assessment VARCHAR(30),

    CONSTRAINT pk_return_line_items PRIMARY KEY (id),
    CONSTRAINT fk_line_items_return_request
        FOREIGN KEY (return_request_id)
        REFERENCES return_management.return_requests(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_line_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_line_items_reason CHECK (
        return_reason IN ('DAMAGED','WRONG_ITEM','CHANGED_MIND','INCOMPLETE','OTHER')
    ),
    CONSTRAINT chk_line_items_condition CHECK (
        condition_assessment IS NULL OR
        condition_assessment IN ('NEW','DAMAGED','INCOMPLETE','FOR_RESALE','FOR_WRITEOFF')
    )
);

CREATE INDEX idx_line_items_return_request_id ON return_management.return_line_items(return_request_id);

COMMENT ON TABLE return_management.return_line_items IS 'Pozycje produktow w zgloszeniu zwrotu';
