CREATE TABLE notification_delivery (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NULL,
    user_id BINARY(16) NULL,
    event_type VARCHAR(60) NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    sent_at TIMESTAMP(6) NULL,
    last_error VARCHAR(1000) NULL,
    CONSTRAINT fk_notification_delivery_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_notification_delivery_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT chk_notification_delivery_status CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    INDEX ix_notification_delivery_tenant_created (tenant_id, created_at),
    INDEX ix_notification_delivery_status_created (status, created_at)
);
