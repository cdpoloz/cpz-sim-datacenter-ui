package com.cpz.sim.datacenter.ui.logging;

import org.jetbrains.annotations.NotNull;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Formats application log records with timestamp, level, source, and message.
 *
 * <p>Line format:</p>
 * <pre>
 * yyyy-MM-dd HH:mm:ss.SSS LEVEL Class.Method > Message
 * </pre>
 *
 * @author CPZ
 */
public class LogFormatter extends SimpleFormatter {

    /** Creates the stateless application log formatter. */
    public LogFormatter() {
    }

    /**
     * Formats a {@link LogRecord} according to the configured line pattern.
     *
     * @param record log record to format
     * @return formatted line for log output
     */
    @Override
    public String format(@NotNull LogRecord record) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                .withZone(ZoneId.systemDefault());
        String methodName = record.getSourceMethodName() != null ? record.getSourceMethodName() : "";
        String className = record.getSourceClassName().substring(record.getSourceClassName().lastIndexOf('.') + 1);
        String origin = className + "." + methodName.replace("lambda$", "").split("\\$")[0];
        return String.format("%1$s %2$s %3$s > %4$s%n",
                dateTimeFormatter.format(record.getInstant()),
                record.getLevel(),
                origin,
                record.getMessage());
    }
}
