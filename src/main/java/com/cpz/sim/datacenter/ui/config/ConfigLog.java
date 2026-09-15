package com.cpz.sim.datacenter.ui.config;

import com.cpz.sim.datacenter.ui.logging.Log;
import com.cpz.sim.datacenter.ui.logging.LogFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;

/**
 * Creates the file and console handlers used by the application logger.
 *
 * @author CPZ
 */
public class ConfigLog {

    /** Creates a logging configuration helper. */
    public ConfigLog() {
    }

    /**
     * Creates a {@link FileHandler} that appends to a date-based file in {@code log}.
     * <p>
     * Uses {@link LogFormatter} and sets the handler level to {@link Level#INFO}.
     * </p>
     *
     * @return a configured {@link FileHandler}, or {@code null} if an I/O error occurs
     */
    @Nullable
    public static FileHandler getLogFileHandler() {
        try {
            Path folder = Paths.get("log");
            if (Files.notExists(folder)) {
                Files.createDirectories(folder);
            }
            LocalDate currentDate = LocalDate.now();
            // The legacy ProcessingTemplate prefix is retained for log-file compatibility.
            String fileName
                    = "ProcessingTemplate_"
                    + currentDate.getYear()
                    + "-"
                    + String.format("%02d", currentDate.getMonthValue())
                    + "-"
                    + String.format("%02d", currentDate.getDayOfMonth())
                    + ".log";
            FileHandler fileHandler = new FileHandler(folder + File.separator + fileName, true);
            fileHandler.setFormatter(new LogFormatter());
            fileHandler.setLevel(Level.INFO);
            return fileHandler;
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    /**
     * Creates a {@link ConsoleHandler} for process-console logging.
     * <p>
     * Uses {@link LogFormatter} and sets the handler level to {@link Level#CONFIG}.
     * </p>
     *
     * @return a configured {@link ConsoleHandler}
     */
    @NotNull
    public static ConsoleHandler getLogConsoleHandler() {
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new LogFormatter());
        consoleHandler.setLevel(Level.CONFIG);
        return consoleHandler;
    }
}
