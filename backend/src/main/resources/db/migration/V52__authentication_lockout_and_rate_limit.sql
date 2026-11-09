ALTER TABLE app_user
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP(6) NULL,
    ADD COLUMN last_login_at TIMESTAMP(6) NULL;

CREATE INDEX ix_app_user_locked_until ON app_user(locked_until);

CREATE TABLE auth_rate_limit_bucket (
    bucket_key CHAR(64) NOT NULL PRIMARY KEY,
    window_started_at TIMESTAMP(6) NOT NULL,
    request_count INT NOT NULL,
    blocked_until TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    INDEX ix_auth_rate_limit_updated (updated_at)
);
