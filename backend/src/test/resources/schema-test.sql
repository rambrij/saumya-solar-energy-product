CREATE TABLE IF NOT EXISTS auth_rate_limit_bucket (
    bucket_key VARCHAR(64) PRIMARY KEY,
    window_started_at TIMESTAMP(6) NOT NULL,
    request_count INTEGER NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

-- MySQL DATE_FORMAT compatibility for VendorAnalyticsService.trends integration probes.
CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR "com.saumyasolar.MySqlDateFormatFunction.format";
