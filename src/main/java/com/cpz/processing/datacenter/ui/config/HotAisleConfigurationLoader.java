package com.cpz.processing.datacenter.ui.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
/**
 * Loads hot aisle configuration from a JSON file.
 *
 * @author CPZ
 */
public final class HotAisleConfigurationLoader {

    private final ObjectMapper objectMapper;

    public HotAisleConfigurationLoader() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
        objectMapper.enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
    }

    /**
     * Loads and validates a hot aisle configuration.
     *
     * @param configurationPath path to the JSON configuration file
     * @return loaded configuration
     * @throws IOException if the file cannot be read or deserialized
     */
    public HotAisleConfiguration load(Path configurationPath) throws IOException {
        Objects.requireNonNull(configurationPath, "Configuration path must not be null");
        if (!Files.isRegularFile(configurationPath)) throw new IOException("Hot aisle configuration file does not exist: " + configurationPath);
        return objectMapper.readValue(configurationPath.toFile(), HotAisleConfiguration.class);
    }
}
