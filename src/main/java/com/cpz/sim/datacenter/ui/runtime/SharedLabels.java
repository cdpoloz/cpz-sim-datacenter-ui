package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.label.LabelFactory;
import com.cpz.processing.controls.controls.label.config.LabelConfigLoader;
import com.cpz.processing.controls.core.util.JsonConfigSupport;
import processing.core.PApplet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Selects shared entries using the same loader and factory as CLASSIC. */
final class SharedLabels {
    static Map<String, Label> load(PApplet sketch, String path, Set<String> codes) {
        var root = JsonConfigSupport.loadRequiredObject(sketch, path, "shared labels");
        var entries = JsonConfigSupport.getRequiredArray(root, "controls", path);
        var loader = new LabelConfigLoader(sketch);
        var result = new LinkedHashMap<String, Label>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getJSONObject(i);
            String code = entry.getString("code", "");
            if (codes.contains(code)) {
                if (result.containsKey(code)) throw new IllegalArgumentException("Duplicate " + code + " in " + path);
                result.put(code, LabelFactory.create(sketch, loader.loadFromJson(entry, path)));
            }
        }
        if (result.size() != codes.size()) {
            var missing = new TreeSet<>(codes);
            missing.removeAll(result.keySet());
            throw new IllegalArgumentException("Missing labels " + missing + " in " + path);
        }
        return result;
    }
}
