-- Supports bounded daily expiry processing without scanning the full warranty table.
CREATE INDEX ix_warranty_status_end_date ON warranty (status, end_date);
