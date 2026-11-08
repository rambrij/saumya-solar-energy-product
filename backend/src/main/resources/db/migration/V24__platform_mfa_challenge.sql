CREATE TABLE mfa_challenge (
    id BINARY(16) PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_mfa_challenge_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    INDEX ix_mfa_challenge_user_created (user_id, created_at),
    INDEX ix_mfa_challenge_expiry (expires_at)
);