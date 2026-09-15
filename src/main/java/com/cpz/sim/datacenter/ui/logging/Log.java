package com.cpz.sim.datacenter.ui.logging;

import com.cpz.sim.datacenter.ui.config.ConfigLog;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application logger configured with the handlers provided by {@link ConfigLog}.
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
