CREATE TABLE identity_access.users (
    id              UUID            NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    role            VARCHAR(30)     NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (
        role IN ('CUSTOMER','WAREHOUSE_WORKER','WAREHOUSE_MANAGER','BOK','ACCOUNTING','ADMIN')
    )
);

COMMENT ON TABLE identity_access.users IS 'Uzytkownicy systemu RMA';
