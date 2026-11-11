CREATE TABLE notification_outbox (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  delivery_id BINARY(16) NOT NULL,
  status VARCHAR(20) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(6) NULL,
  last_error VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uq_notification_outbox_delivery UNIQUE (delivery_id),
  CONSTRAINT fk_notification_outbox_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_notification_outbox_delivery FOREIGN KEY (delivery_id) REFERENCES notification_delivery(id),
  INDEX ix_notification_outbox_dispatch (status,next_attempt_at,created_at),
  INDEX ix_notification_outbox_tenant (tenant_id,status,created_at)
);

CREATE TABLE notification_preference (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  consented BOOLEAN NOT NULL DEFAULT FALSE,
  consent_updated_at TIMESTAMP(6) NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uq_notification_preference UNIQUE (tenant_id,user_id,channel),
  CONSTRAINT fk_notification_preference_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_notification_preference_user FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id),
  INDEX ix_notification_preference_user (tenant_id,user_id)
);

CREATE TABLE notification_provider_event (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  delivery_id BINARY(16) NOT NULL,
  provider_code VARCHAR(40) NOT NULL,
  provider_event_id VARCHAR(160) NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  delivery_status VARCHAR(20) NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  received_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uq_notification_provider_event UNIQUE (provider_code,provider_event_id),
  CONSTRAINT fk_notification_provider_event_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_notification_provider_event_delivery FOREIGN KEY (delivery_id) REFERENCES notification_delivery(id),
  INDEX ix_notification_provider_event_delivery (tenant_id,delivery_id,received_at)
);

ALTER TABLE notification_delivery DROP CHECK chk_notification_delivery_status;
ALTER TABLE notification_delivery ADD CONSTRAINT chk_notification_delivery_status CHECK (status IN ('PENDING','DELIVERED','FAILED','DEAD_LETTER','SUPPRESSED'));
