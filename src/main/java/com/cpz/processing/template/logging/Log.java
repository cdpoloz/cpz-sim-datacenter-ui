package com.cpz.processing.template.logging;

import com.cpz.processing.template.config.ConfigLog;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logging infrastructure component ({@code logging} package) that configures the handlers defined
 * in {@link ConfigLog} and exposes a preconfigured {@link Logger}.
 * <p>
 * This class does not participate in MVVM logic. It only centralizes logging configuration.
 * </p>
 *
 * @author CPZ
 */
public class Log extends Logger {

    /**
     * Creates a logger with the configured console and file handlers.
     *
     * @param name logger name
     */
    public Log(String name) {
        super(name, null);
        addHandler(ConfigLog.getLogConsoleHandler());
        addHandler(ConfigLog.getLogFileHandler());
        setLevel(Level.ALL);
    }

}
