CREATE TABLE identity_access.audit_log (
    id          UUID            NOT NULL,
    user_id     UUID,
    rma_number  VARCHAR(20),
    action      VARCHAR(100)    NOT NULL,
    ip_address  VARCHAR(45),
    occurred_at TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

CREATE INDEX idx_audit_log_rma_number  ON identity_access.audit_log(rma_number) WHERE rma_number IS NOT NULL;
CREATE INDEX idx_audit_log_user_id     ON identity_access.audit_log(user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_audit_log_occurred_at ON identity_access.audit_log(occurred_at DESC);

COMMENT ON TABLE identity_access.audit_log IS 'Log audytu: kto, kiedy, z jakiego IP, co zrobil';
