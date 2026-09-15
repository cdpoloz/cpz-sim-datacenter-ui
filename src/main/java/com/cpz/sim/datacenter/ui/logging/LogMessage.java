package com.cpz.sim.datacenter.ui.logging;

import org.jetbrains.annotations.NotNull;

/**
 * Provides standardized messages for startup and I/O failures.
 *
 * @author CPZ
 */
public class LogMessage {

    /** Creates a logging-message helper. */
    public LogMessage() {
    }

    /**
     * Builds a message indicating that a file could not be loaded.
     *
     * @param path path of the file that could not be loaded
     * @return formatted error message
     */
    @NotNull
    public static String fileLoadError(String path) {
        StringBuilder sb = new StringBuilder();
        sb.append("Could not load file: ").append(path);
        return sb.toString();
    }

}
