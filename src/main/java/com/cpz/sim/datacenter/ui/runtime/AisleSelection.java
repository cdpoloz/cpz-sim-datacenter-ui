package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.geometry.ControlBounds;
import com.cpz.sim.datacenter.config.definition.HotAisleDefinition;
import com.cpz.sim.datacenter.model.RackLocation;
import java.util.*;

/** Selection and layout of declared aisles; column order follows the definition. */
final class AisleSelection {
    record Geometry(Map<String, ControlBounds> columns, Map<RackLocation, ControlBounds> racks) { }
    private final RoomMap room;
    private int index = -1;
    AisleSelection(RoomMap room) { this.room = room; }
    HotAisleDefinition selected() { return index < 0 ? null : room.hotAisles().get(index); }
    boolean canPrevious() { return index > 0; }
    boolean canNext() { return index + 1 < room.hotAisles().size(); }
    void selectRack(RackLocation location) {
        room.select(location);
        index = -1;
        for (int i = 0; i < room.hotAisles().size(); i++) {
            if (room.hotAisles().get(i).columns().contains(location.column())) { index = i; break; }
        }
    }
    void change(int direction) {
        int next = index < 0 ? (direction > 0 ? 0 : -1) : index + direction;
        if (next < 0 || next >= room.hotAisles().size()) return;
        index = next;
        if (room.selected() != null && !selected().columns().contains(room.selected().column())) room.clearSelection();
    }
    Geometry geometry(RuntimeLayout layout, int width, int height) {
        var aisle = selected();
        if (aisle == null || aisle.columns().isEmpty()) return new Geometry(Map.of(), Map.of());
        var b = layout.contentBounds(RuntimeLayout.Area.SELECTED_AISLE).resolve(width, height);
        var racks = room.racks().stream().filter(r -> aisle.columns().contains(r.location().column())).toList();
        var rows = racks.stream().map(RoomMap.Rack::row).distinct().sorted().toList();
        float columnWidth = b.width() / aisle.columns().size();
        var columns = new LinkedHashMap<String, ControlBounds>();
        for (int i = 0; i < aisle.columns().size(); i++)
            columns.put(aisle.columns().get(i), relative(b.x()+i*columnWidth, b.y()+b.height()*.35f, columnWidth, b.height()*.06f, width, height));
        var placements = new LinkedHashMap<RackLocation, ControlBounds>();
        if (!rows.isEmpty()) {
            float rowHeight = b.height() * .59f / rows.size();
            for (var rack : racks) {
                float x = b.x() + (aisle.columns().indexOf(rack.location().column())+.5f)*columnWidth;
                float y = b.y()+b.height()*.41f+(rows.indexOf(rack.row())+.5f)*rowHeight;
                placements.put(rack.location(), relative(x, y, columnWidth*.82f, rowHeight*.82f, width, height));
            }
        }
        return new Geometry(Collections.unmodifiableMap(columns), Collections.unmodifiableMap(placements));
    }
    private static ControlBounds relative(float x, float y, float w, float h, int width, int height) {
        return ControlBounds.relative(x/width, y/height, w/height, h/height);
    }
}
