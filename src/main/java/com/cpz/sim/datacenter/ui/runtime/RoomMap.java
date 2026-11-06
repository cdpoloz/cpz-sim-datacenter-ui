package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.sim.datacenter.config.definition.*;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.processing.controls.controls.geometry.ControlBounds;
import java.nio.file.Path;
import java.util.*;

/** Topology and geometry for the room, independent of rendering and input. */
public final class RoomMap {
    public record Rack(RackLocation location, String row, List<String> slots) { }
    public record Placement(Rack rack, ControlBounds bounds) { }
    public record Geometry(Map<String, ControlBounds> columns, List<Placement> racks) { }
    private final List<String> columns, rows;
    private final List<Rack> racks;
    private final List<HotAisleDefinition> hotAisles;
    private RackLocation selected;

    public static RoomMap load() {
        return new RoomMap(loadDefinition().layout());
    }
    public static DatacenterDefinition loadDefinition() {
        Path path = Path.of(System.getProperty("simulation.datacenter.config", "data/config/datacenter.json"));
        try { return new JsonDatacenterConfigLoader().load(path); }
        catch (Exception e) { throw new IllegalStateException("Cannot load room layout from " + path + ": " + e.getMessage(), e); }
    }
    public RoomMap(DatacenterLayoutDefinition layout) {
        Objects.requireNonNull(layout, "layout");
        var byLocation = new HashSet<RackLocation>();
        var positions = new HashSet<List<String>>();
        var cols = new TreeSet<String>();
        var rowSet = new TreeSet<String>();
        var entries = new ArrayList<Rack>();
        for (var definition : layout.racks()) {
            var location = new RackLocation(definition.column(), definition.code());
            if (!byLocation.add(location)) throw new IllegalArgumentException("Duplicate rack: " + location);
            if (definition.row() == null || definition.row().isBlank()) throw new IllegalArgumentException("Missing row for " + location);
            if (!positions.add(List.of(definition.column(), definition.row()))) throw new IllegalArgumentException("Duplicate row " + definition.row() + " in column " + definition.column());
            cols.add(definition.column()); rowSet.add(definition.row());
            entries.add(new Rack(location, definition.row(), List.copyOf(RackSlotResolver.resolveSlotCodes(definition))));
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("Room layout contains no racks");
        entries.sort(Comparator.comparing((Rack r) -> r.location().column()).thenComparing(Rack::row).thenComparing(r -> r.location().code()));
        columns = List.copyOf(cols); rows = List.copyOf(rowSet); racks = List.copyOf(entries);
        hotAisles = layout.hotAisles() == null ? List.of() : List.copyOf(layout.hotAisles());
    }
    public List<String> columns() { return columns; }
    public List<Rack> racks() { return racks; }
    public List<HotAisleDefinition> hotAisles() { return hotAisles; }
    public RackLocation selected() { return selected; }
    public void select(RackLocation location) {
        if (racks.stream().noneMatch(r -> r.location().equals(location))) throw new IllegalArgumentException("Unknown rack: " + location);
        selected = location;
    }
    /** Lexical column/row order; shared row values align across columns, including gaps. */
    public Geometry geometry(RuntimeLayout layout, int width, int height) {
        var content = layout.contentBounds(RuntimeLayout.Area.ROOM).resolve(width, height);
        float colWidth = content.width() / columns.size();
        float header = content.height() * .08f, footer = content.height() * .09f;
        float rowHeight = (content.height() - header - footer) / rows.size();
        var colBounds = new LinkedHashMap<String, ControlBounds>();
        for (int i = 0; i < columns.size(); i++)
            colBounds.put(columns.get(i), relative(content.x() + i * colWidth, content.y(), colWidth, header, width, height));
        var placements = new ArrayList<Placement>();
        for (var rack : racks) {
            float x = content.x() + (columns.indexOf(rack.location().column()) + .5f) * colWidth;
            float y = content.y() + header + (rows.indexOf(rack.row()) + .5f) * rowHeight;
            // Button coordinates are centres; Label coordinates are top left.
            placements.add(new Placement(rack, relative(x, y, colWidth * .82f, rowHeight * .82f, width, height)));
        }
        return new Geometry(Collections.unmodifiableMap(colBounds), List.copyOf(placements));
    }
    private static ControlBounds relative(float x, float y, float w, float h, int width, int height) {
        return ControlBounds.relative(x / width, y / height, w / height, h / height);
    }
}
