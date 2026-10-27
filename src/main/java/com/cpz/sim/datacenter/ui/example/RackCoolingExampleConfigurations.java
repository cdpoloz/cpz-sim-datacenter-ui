package com.cpz.sim.datacenter.ui.example;

import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.config.validation.DatacenterConfigValidator;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Generates complete UI-compatible JSON variants without changing the source configuration. */
public final class RackCoolingExampleConfigurations {
    private static final String[] VARIANTS = {
            "NO_UNIT", "DISABLED", "ROOM", "EXTERNAL", "ROOM_WITH_HVAC", "EXTERNAL_WITH_HVAC"
    };

    private RackCoolingExampleConfigurations() { }

    public static Map<String, Path> generate(Path source, Path outputDirectory) throws IOException {
        var loader = new JsonDatacenterConfigLoader();
        var validator = new DatacenterConfigValidator();
        var definition = loader.load(source);
        validator.validate(definition);
        if (definition.rackCooling() == null || definition.cooling() == null)
            throw new IllegalArgumentException("source requires rack and room cooling templates");
        var mapper = JsonMapper.builder().build();
        var template = (ObjectNode) mapper.readTree(Files.readString(source));
        // Validate destinations before creating files, including accidental source replacement.
        for (var variant : VARIANTS) {
            var output = outputDirectory.resolve(variant.toLowerCase(Locale.ROOT) + ".json");
            if (output.toAbsolutePath().normalize().equals(source.toAbsolutePath().normalize())
                    || Files.exists(output) && Files.isSameFile(output, source))
                throw new IllegalArgumentException("output would replace the source configuration: " + output);
        }
        Files.createDirectories(outputDirectory);
        Map<String, Path> generated = new LinkedHashMap<>();
        for (var variant : VARIANTS) {
            ObjectNode root = template.deepCopy();
            if (variant.equals("NO_UNIT")) root.remove("rackCooling");
            else for (var unit : root.path("rackCooling").path("units")) {
                ((ObjectNode) unit).put("initiallyEnabled", !variant.equals("DISABLED"));
                ((ObjectNode) unit).put("heatRejectionDestination", variant.startsWith("EXTERNAL") ? "EXTERNAL" : "ROOM");
            }
            for (String group : new String[]{"supplyUnits", "exhaustUnits"})
                for (var unit : root.path("cooling").path(group))
                    ((ObjectNode) unit).put("initiallyEnabled", variant.endsWith("_WITH_HVAC"));
            var output = outputDirectory.resolve(variant.toLowerCase(Locale.ROOT) + ".json");
            Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n");
            validator.validate(loader.load(output));
            generated.put(variant, output);
        }
        return Map.copyOf(generated);
    }

    /** Optional source path and output directory; generated data defaults to target/. */
    public static void main(String[] args) throws IOException {
        var source = Path.of(args.length > 0 ? args[0] : "data/config/datacenter.json");
        var output = Path.of(args.length > 1 ? args[1] : "target/rack-cooling-scenarios");
        generate(source, output).entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> System.out.println(entry.getKey() + ": " + entry.getValue()));
    }
}
