CREATE TABLE platform_setting (
  setting_key VARCHAR(60) NOT NULL PRIMARY KEY,
  value_type VARCHAR(20) NOT NULL,
  string_value VARCHAR(500) NOT NULL,
  version BIGINT NOT NULL DEFAULT 1,
  updated_by BINARY(16) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_platform_setting_actor FOREIGN KEY (updated_by) REFERENCES app_user(id),
  CONSTRAINT chk_platform_setting_type CHECK (value_type IN ('STRING','EMAIL','PHONE','CURRENCY','TIME_ZONE','LANGUAGE','BANNER')),
  CONSTRAINT chk_platform_setting_version CHECK (version >= 1)
);
