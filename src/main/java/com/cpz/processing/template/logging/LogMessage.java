package com.cpz.processing.template.logging;

import org.jetbrains.annotations.NotNull;

/**
 * Logging infrastructure utility ({@code logging} package) that provides standardized error
 * messages for application bootstrap and I/O operations.
 * <p>
 * This is a stateless utility and contains no business logic.
 * </p>
 *
 * @author CPZ
 */
public class LogMessage {

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
