package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.controls.geometry.ControlMeasure;
import com.cpz.processing.controls.core.input.PointerEvent;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.model.ServerRole;
import com.cpz.sim.datacenter.ui.ui.HealthPresentation;
import com.cpz.utils.color.Colors;
import processing.core.PApplet;
import processing.core.PFont;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.cpz.sim.datacenter.ui.util.Constants.*;

final class RoomMapPanel {
    private static final Set<String> MAP_LABELS = Set.of("lblRoomAirTemperature", "lblRoomAirTemperatureValue",
            "lblRoomTemperatureScale", "lblRoomTemperatureScale01", "lblRoomTemperatureScale02",
            "lblRoomTemperatureScale03", "lblRoomTemperatureScale04", "lblRoomTemperatureScale05",
            "lblRoomTemperatureScale06", "lblSupply", "lblExhaust", "lblHotspot", "lblAI", "lblOffline", "lblEmpty");
    private static final Set<String> HEADER_LABELS = Set.of("lblRoom", "lblRoomTotalServers",
            "lblRoomTotalServersValue", "lblRoomOnlineServers", "lblRoomOnlineServersValue");

    private record RackControl(Button button, RoomRackIndicators indicators) {}

    private final PApplet sketch;
    private final PFont font;
    private final RoomMapArtwork artwork;
    private final RoomMap room;
    private final RuntimeSession session;
    private final Map<String, Label> mapLabels, headerLabels;
    private final Map<Label, Float> labelSizes = new LinkedHashMap<>();
    private final Map<RackLocation, RackControl> racks = new LinkedHashMap<>();
    private final Set<RackLocation> aiRacks, cooledRacks;
    private RoomMap.Geometry geometry;
    private String selectedText = "";

    RoomMapPanel(PApplet sketch, PFont font, RoomMap room, RuntimeSession session, RuntimeLayout layout,
                 Consumer<RackLocation> select) {
        this.sketch = sketch;
        this.font = font;
        this.room = room;
        this.session = session;
        this.artwork = new RoomMapArtwork(sketch);
        mapLabels = SharedLabels.load(sketch, "data/config/labels.json", MAP_LABELS);
        headerLabels = SharedLabels.load(sketch, "data/config/labels.json", HEADER_LABELS);
        for (var labels : List.of(mapLabels, headerLabels)) {
            labels.values().forEach(label -> labelSizes.put(label, label.getStyleConfig().textSize / sketch.height));
        }
        var definedRoom = session.definition().layout().room();
        String roomName = definedRoom == null || definedRoom.name() == null ? "" : definedRoom.name();
        headerLabels.get("lblRoom").setText("DATACENTER MAP" + (roomName.isBlank() ? "" : " - " + roomName));
        var range = session.range();
        for (int i = 0; i < 6; i++) {
            float temperature = PApplet.lerp(range.minServerTemperatureCelsius(), range.maxServerTemperatureCelsius(), i / 5f);
            mapLabels.get("lblRoomTemperatureScale0" + (i + 1)).setText(Math.round(temperature) + "°C");
        }
        aiRacks = session.serverRoles().entrySet().stream().filter(entry -> entry.getValue() == ServerRole.AI)
                .map(entry -> entry.getKey().rackLocation()).collect(Collectors.toUnmodifiableSet());
        cooledRacks = room.racks().stream().map(RoomMap.Rack::location).filter(location -> session.rackCooling(location).isPresent())
                .collect(Collectors.toUnmodifiableSet());
        var styles = RoomRackIndicators.loadStyles(sketch);
        geometry = room.geometry(layout, sketch.width, sketch.height);
        for (var placement : geometry.racks()) {
            var location = placement.rack().location();
            var button = new Button(sketch, location.rackCode().value(), placement.bounds());
            button.setClickListener(() -> select.accept(location));
            racks.put(location, new RackControl(button, new RoomRackIndicators(sketch, location, styles)));
        }
        resize(layout);
    }

    void resize(RuntimeLayout layout) {
        geometry = room.geometry(layout, sketch.width, sketch.height);
        for (var placement : geometry.racks()) {
            var control = racks.get(placement.rack().location());
            control.button().setBounds(placement.bounds());
            control.indicators().place(placement.bounds().resolve(sketch.width, sketch.height));
        }
        float scale = Math.min(1, ((float) sketch.width / sketch.height) / (1920f / 1014));
        labelSizes.forEach((label, size) -> label.setTextSize(ControlMeasure.relative(size * scale)));
    }

    void refresh() {
        var snapshot = session.snapshot();
        var operational = snapshot.operationalSnapshot();
        var critical = HealthPresentation.criticalRacks(snapshot.healthSnapshot());
        racks.forEach((location, control) -> {
            var rack = operational.racks().get(location);
            int color = rack == null ? RackDisplay.UNAVAILABLE
                    : !rack.hasInstalledServers() ? COLOR_EMPTY_RACK
                    : critical.contains(location) ? COLOR_HOTSPOT_RACK
                    : !rack.hasOnlineServers() ? COLOR_MIN_TEMPERATURE
                    : thermalColor(rack.averageOnlineTemperatureCelsius());
            control.indicators().update(rack, color, critical.contains(location), aiRacks.contains(location), cooledRacks.contains(location));
        });
        headerLabels.get("lblRoomTotalServersValue").setText(Integer.toString(operational.racks().values().stream()
                .mapToInt(rack -> rack.installedServerCount()).sum()));
        headerLabels.get("lblRoomOnlineServersValue").setText(Integer.toString(operational.racks().values().stream()
                .mapToInt(rack -> rack.onlineServerCount()).sum()));
        double air = operational.roomAirTemperatureCelsius();
        var airLabel = mapLabels.get("lblRoomAirTemperatureValue");
        airLabel.setText(Double.isFinite(air) ? session.formatTemperature(air) : "--");
        airLabel.setTextColor(Double.isFinite(air) ? thermalColor(air) : COLOR_WHITE_LABEL);
        updateSelection();
    }

    void updateSelection() {
        var selected = room.selected();
        if (selected == null) {
            selectedText = "No rack selected";
            return;
        }
        double temperature = RackDisplay.temperature(session.snapshot().operationalSnapshot().racks().get(selected));
        selectedText = selected.column() + " / " + selected.rackCode().value() + "  "
                + (Double.isFinite(temperature) ? session.formatTemperature(temperature) : "--");
    }

    private int thermalColor(double temperature) {
        if (!Double.isFinite(temperature)) return RackDisplay.UNAVAILABLE;
        var range = session.range();
        float min = range.minServerTemperatureCelsius(), max = range.maxServerTemperatureCelsius();
        return Colors.lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE,
                max > min ? Math.clamp((float) ((temperature - min) / (max - min)), 0, 1) : 0);
    }

    void pointer(PointerEvent event) {
        racks.values().forEach(control -> control.button().handlePointerEvent(event));
    }

    void draw(RuntimeLayout layout, boolean cooling) {
        artwork.draw(sketch, layout, cooling);
        sketch.pushStyle();
        sketch.textFont(font);
        // The room name comes from JSON and the counter labels retain their CLASSIC anchors.
        sketch.imageMode(PApplet.CORNER);
        sketch.clip(sketch.width * .04f, sketch.height * .08f, sketch.width * .18f, sketch.height * .036f);
        headerLabels.get("lblRoom").draw();
        sketch.noClip();
        headerLabels.forEach((code, label) -> { if (!code.equals("lblRoom")) label.draw(); });
        if (!cooling) {
            mapLabels.values().forEach(Label::draw);
            drawGrid(layout);
        }
        sketch.popStyle();
    }

    private void drawGrid(RuntimeLayout layout) {
        int w = sketch.width, h = sketch.height;
        var content = layout.contentBounds(RuntimeLayout.Area.ROOM).resolve(w, h);
        float scale = Math.min(w / 3840f, h / 1986f);
        sketch.rectMode(PApplet.CORNER);
        sketch.textAlign(PApplet.CENTER, PApplet.CENTER);
        sketch.textSize(Math.min(23 * scale, content.width() / geometry.columns().size() / 5));
        geometry.columns().forEach((column, bounds) -> {
            var b = bounds.resolve(w, h);
            float x = b.x() + b.width() / 2;
            boolean selected = room.selected() != null && column.equals(room.selected().column());
            sketch.noFill();
            sketch.stroke(selected ? 0xff00e0e0 : COLOR_BLUE_LABEL);
            sketch.strokeWeight((selected ? 3 : 2) * scale);
            sketch.rect(x - b.width() * .275f, b.y() + b.height(), b.width() * .55f,
                    content.height() - b.height(), 5 * scale);
            sketch.fill(COLOR_WHITE_LABEL);
            sketch.text(column, x, b.y() + b.height() * .42f);
        });
        sketch.textAlign(PApplet.RIGHT, PApplet.CENTER);
        sketch.textSize(Math.min(23 * scale, content.height() / geometry.rows().size() * .52f));
        geometry.rows().forEach((row, bounds) -> {
            var b = bounds.resolve(w, h);
            sketch.text(row, sketch.width * .044f, b.y());
        });
        for (var placement : geometry.racks()) {
            var rack = placement.rack();
            var b = placement.bounds().resolve(w, h);
            racks.get(rack.location()).indicators().draw();
            if (rack.location().equals(room.selected())) {
                sketch.noFill();
                sketch.stroke(0xff8ddcff);
                sketch.strokeWeight(3 * scale);
                sketch.rect(b.x() - b.width() / 2, b.y() - b.height() / 2, b.width(), b.height(), 5 * scale);
            }
            String code = rack.location().rackCode().value();
            if (!code.equals(rack.row())) {
                sketch.fill(COLOR_WHITE_LABEL);
                sketch.textAlign(PApplet.CENTER, PApplet.CENTER);
                sketch.textSize(Math.min(b.height() * .6f, b.width() * .55f / Math.max(1, code.length()) / .62f));
                sketch.text(code, b.x(), b.y());
            }
        }
        sketch.fill(COLOR_WHITE_LABEL);
        sketch.textAlign(PApplet.LEFT, PApplet.CENTER);
        sketch.textSize(23 * scale);
        // Keep the selection between the rack grid and the artwork's legend.
        sketch.text(selectedText, w * .036f, h * 1112f / 1986f);
    }
}
