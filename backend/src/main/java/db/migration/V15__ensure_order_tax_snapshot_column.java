package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Ensures the order tax snapshot column exists.
 *
 * MySQL DDL is not transactional: V14 may have added this column before a later
 * statement failed. Inspect metadata before altering so the migration safely
 * completes in either the clean or partially-migrated case.
 */
public class V15__ensure_order_tax_snapshot_column extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        DatabaseMetaData metadata = context.getConnection().getMetaData();
        boolean exists = false;
        try (ResultSet columns = metadata.getColumns(
                context.getConnection().getCatalog(), null, "order_item", "tax_percent")) {
            exists = columns.next();
        }
        if (!exists) {
            try (Statement statement = context.getConnection().createStatement()) {
                statement.executeUpdate(
                        "ALTER TABLE order_item ADD COLUMN tax_percent DECIMAL(5,2) NOT NULL DEFAULT 0");
            }
        }
    }
}
