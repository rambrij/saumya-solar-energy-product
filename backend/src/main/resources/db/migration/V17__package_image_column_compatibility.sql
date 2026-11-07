-- V16 adds the optional package image column. This migration intentionally
-- provides a no-op schema checkpoint for installations that need to verify
-- the new package-image contract independently.
SELECT 1;
