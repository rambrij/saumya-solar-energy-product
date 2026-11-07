-- MySQL DDL is not transactional. V14 may have added this column before a later
-- statement failed, so make the tax snapshot column addition idempotent.
SET @tax_percent_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'order_item'
      AND column_name = 'tax_percent'
);
SET @tax_percent_ddl = IF(
    @tax_percent_exists = 0,
    'ALTER TABLE order_item ADD COLUMN tax_percent DECIMAL(5,2) NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE tax_percent_stmt FROM @tax_percent_ddl;
EXECUTE tax_percent_stmt;
DEALLOCATE PREPARE tax_percent_stmt;
