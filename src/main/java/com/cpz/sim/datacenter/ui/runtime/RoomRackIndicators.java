package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.geometry.ControlBounds;
import com.cpz.processing.controls.controls.geometry.ResolvedBounds;
import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.indicator.IndicatorFactory;
import com.cpz.processing.controls.controls.indicator.config.IndicatorConfig;
import com.cpz.processing.controls.controls.indicator.config.IndicatorConfigLoader;
import com.cpz.processing.controls.core.util.JsonConfigSupport;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import processing.core.PApplet;

import java.util.EnumMap;
import java.util.Map;

final class RoomRackIndicators {
    enum Part {
        BODY(""), OFFLINE("Offline"), EMPTY("Empty"), HOTSPOT("Hotspot"), AI("AI"), COOLING("Cooling");

        final String prefix;

        Part(String suffix) {
            prefix = "indRack" + suffix;
        }
    }

    private final Map<Part, Indicator> indicators = new EnumMap<>(Part.class);

    static Map<Part, IndicatorConfig> loadStyles(PApplet sketch) {
        var styles = new EnumMap<Part, IndicatorConfig>(Part.class);
        var loader = new IndicatorConfigLoader(sketch);
        for (String path : new String[]{"data/config/indicatorsRack.json", "data/config/indicatorsRackCondition.json"}) {
            var root = JsonConfigSupport.loadRequiredObject(sketch, path, "room rack styles");
            var entries = JsonConfigSupport.getRequiredArray(root, "controls", path);
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.getJSONObject(i);
                for (var part : Part.values()) {
                    if ((part.prefix + "C01R01").equals(entry.getString("code", ""))) {
                        styles.put(part, loader.loadFromJson(entry, path));
                    }
                }
            }
        }
        if (styles.size() != Part.values().length) {
            throw new IllegalArgumentException("Missing CLASSIC rack indicator styles");
        }
        return styles;
    }

    RoomRackIndicators(PApplet sketch, RackLocation location, Map<Part, IndicatorConfig> styles) {
        styles.forEach((part, style) -> indicators.put(part, IndicatorFactory.create(sketch,
                new IndicatorConfig(part.prefix + "/" + location.column() + "/" + location.rackCode().value(),
                        style.isOn(), style.getOnColor(), style.getOffColor(), style.getStrokeColor(),
                        style.getStrokeWeight(), ControlBounds.absolute(0, 0, 0, 0), style.isEnabled(),
                        style.isVisible(), style.getRenderer(), null))));
    }

    void place(ResolvedBounds rack) {
        indicators.forEach((part, indicator) -> indicator.setBounds(bounds(part, rack)));
    }

    static ControlBounds bounds(Part part, ResolvedBounds rack) {
        float left = rack.x() - rack.width() / 2;
        float top = rack.y() - rack.height() / 2;
        if (part == Part.BODY) return ControlBounds.absolute(left, top, rack.width(), rack.height());

        float size = Math.min(rack.height() * .56f, rack.width() * .24f);
        float width = part == Part.COOLING ? size * .875f : part == Part.AI ? size * .435f : size;
        float height = part == Part.HOTSPOT ? size * .875f : part == Part.AI ? width : size;
        return ControlBounds.absolute(switch (part) {
            case AI -> left + rack.width() - width - size * .15f;
            case COOLING -> rack.x() - width / 2;
            default -> left + size * .16f;
        }, part == Part.AI ? top + size * .15f : rack.y() - height / 2, width, height);
    }

    void update(RackOperationalSnapshot rack, int color, boolean critical, boolean ai, boolean cooling) {
        boolean empty = rack != null && !rack.hasInstalledServers();
        boolean offline = rack != null && rack.hasInstalledServers() && !rack.hasOnlineServers();
        indicators.get(Part.BODY).setOnColor(color);
        indicators.get(Part.EMPTY).setOn(empty);
        indicators.get(Part.OFFLINE).setOn(offline);
        indicators.get(Part.HOTSPOT).setOn(critical && !empty && !offline);
        indicators.get(Part.AI).setOn(ai);
        indicators.get(Part.COOLING).setOn(cooling);
    }

    void draw() {
        indicators.values().forEach(Indicator::draw);
    }
}
