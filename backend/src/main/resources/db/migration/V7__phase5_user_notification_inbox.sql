-- User-facing inbox projection; separate from provider delivery attempts in notification_delivery.
CREATE TABLE user_notification (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NULL,
  user_id BINARY(16) NOT NULL,
  event_type VARCHAR(60) NOT NULL,
  title VARCHAR(255) NOT NULL,
  body VARCHAR(4000) NOT NULL,
  dedupe_key CHAR(36) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  read_at TIMESTAMP(6) NULL,
  CONSTRAINT uq_user_notification_dedupe UNIQUE (dedupe_key),
  CONSTRAINT fk_user_notification_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_user_notification_user FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id),
  INDEX ix_user_notification_inbox (tenant_id,user_id,created_at),
  INDEX ix_user_notification_unread (tenant_id,user_id,read_at,created_at)
);
