package com.cpz.sim.datacenter.ui.main;

import com.cpz.sim.datacenter.ui.logging.Log;
import com.cpz.sim.datacenter.ui.logging.LogMessage;
import processing.core.PApplet;
import com.cpz.sim.datacenter.ui.dataset.DatasetReplayOptions;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

/**
 * Process entry point that loads global properties and starts the selected Processing view.
 *
 * <p>{@link TemplateSketch} is intentionally retained as a reference but is not launched by
 * this class.</p>
 *
 * @author CPZ
 */
public class Launcher {

    /** Creates a launcher instance; normal startup uses {@link #main(String[])}. */
    public Launcher() {
    }

    /** Application logger shared by startup and the Processing sketch. */
    public static final Log LOG = new Log(Launcher.class.getName());
    /** Properties loaded from {@code data/config.properties} before Processing starts. */
    public static final Properties PROPS = new Properties();

    /**
     * Application entry point.
     * <p>
     * Sets a stable formatting locale, loads application properties, and hands control to
     * Processing with the selected {@link PApplet} implementation.
     * </p>
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        Locale.setDefault(Locale.forLanguageTag("en-US"));
        // File handlers must be closed so buffered log output is flushed on process exit.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (var handler : LOG.getHandlers()) handler.close();
        }));
        String propertiesPath = "data" + File.separator + "config.properties";
        try (Reader reader = new InputStreamReader(new FileInputStream(propertiesPath), StandardCharsets.UTF_8)) {
            PROPS.load(reader);
        } catch (IOException e) {
            LOG.severe(LogMessage.fileLoadError(propertiesPath));
            System.exit(1);
        }
        PApplet.main(selectSketch(System.getProperties()));
    }

    /** Selects the view before constructing any sketch or map controls. */
    public static Class<? extends PApplet> selectSketch(Properties properties) {
        return DatasetReplayOptions.source(properties) == DatasetReplayOptions.Source.DATASET
                ? DatasetReplaySketch.class : Sketch.class;
    }

}
