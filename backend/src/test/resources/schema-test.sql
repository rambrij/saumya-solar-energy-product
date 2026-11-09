CREATE TABLE IF NOT EXISTS auth_rate_limit_bucket (
    bucket_key VARCHAR(64) PRIMARY KEY,
    window_started_at TIMESTAMP(6) NOT NULL,
    request_count INTEGER NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);
