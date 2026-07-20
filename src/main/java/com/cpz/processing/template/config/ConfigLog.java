package com.cpz.processing.template.config;

import com.cpz.processing.template.logging.Log;
import com.cpz.processing.template.logging.LogFormatter;
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
 * Bootstrap configuration ({@code config} package) for the logging infrastructure.
 * <p>
 * Provides preconfigured handlers for file and console output. This class contains no business
 * logic and is used by {@link Log}.
 * </p>
 *
 * @author CPZ
 */
public class ConfigLog {

    /**
     * Creates a {@link FileHandler} that writes to the {@code log} folder using the current date
     * in the file name.
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
     * Creates a {@link ConsoleHandler} for stdout logging.
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
