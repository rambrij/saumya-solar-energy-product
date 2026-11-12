package com.saumyasolar;

import java.sql.Timestamp;
import java.time.temporal.WeekFields;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * H2 test-database compatibility for the MySQL DATE_FORMAT function used by
 * VendorAnalyticsService. Production continues to execute native MySQL DATE_FORMAT.
 */
public final class MySqlDateFormatFunction {
    private MySqlDateFormatFunction() {
    }

    public static String format(Timestamp timestamp, String pattern) {
        if (timestamp == null || pattern == null) return null;
        var value = timestamp.toLocalDateTime();
        return switch (pattern) {
            case "%Y-%m-%d" -> value.format(DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT));
            case "%Y-%m" -> value.format(DateTimeFormatter.ofPattern("uuuu-MM", Locale.ROOT));
            case "%x-W%v" -> {
                var fields = WeekFields.ISO;
                int weekYear = value.get(fields.weekBasedYear());
                int week = value.get(fields.weekOfWeekBasedYear());
                yield "%04d-W%02d".formatted(weekYear, week);
            }
            default -> throw new IllegalArgumentException("Unsupported DATE_FORMAT pattern in tests: " + pattern);
        };
    }
}
