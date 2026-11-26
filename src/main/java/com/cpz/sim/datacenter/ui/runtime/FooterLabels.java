package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.label.LabelFactory;
import com.cpz.processing.controls.controls.label.config.LabelConfigLoader;
import com.cpz.processing.controls.core.util.JsonConfigSupport;
import processing.core.PApplet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Selects only the footer entries, using the same loader and factory as CLASSIC. */
final class FooterLabels {
    static Map<String, Label> load(PApplet sketch, String path) {
        var root = JsonConfigSupport.loadRequiredObject(sketch, path, "footer labels");
        var entries = JsonConfigSupport.getRequiredArray(root, "controls", path);
        var loader = new LabelConfigLoader(sketch);
        var result = new LinkedHashMap<String, Label>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getJSONObject(i);
            String code = entry.getString("code", "");
            if (code.equals("lblSimulation") || code.equals("lblSimulationValue")) {
                if (result.containsKey(code)) throw new IllegalArgumentException("Duplicate " + code + " in " + path);
                result.put(code, LabelFactory.create(sketch, loader.loadFromJson(entry, path)));
            }
        }
        if (result.size() != 2) throw new IllegalArgumentException("Missing footer labels in " + path);
        return result;
    }
}
