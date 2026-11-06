package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.controls.button.config.ButtonStyleConfig;
import com.cpz.processing.controls.controls.button.style.DefaultButtonStyle;
import com.cpz.processing.controls.controls.geometry.ControlMeasure;
import com.cpz.processing.controls.core.input.PointerEvent;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.sim.datacenter.snapshot.RackOperationalSnapshot;
import com.cpz.processing.controls.controls.label.config.LabelStyleConfig;
import com.cpz.processing.controls.controls.label.style.DefaultLabelStyle;
import com.cpz.processing.controls.controls.label.style.VerticalAlign;
import processing.core.PApplet;
import processing.core.PFont;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.cpz.sim.datacenter.ui.runtime.RuntimeLayout.Area.*;
import static com.cpz.sim.datacenter.ui.util.Constants.*;

/** Composes the shell and room map using public controls. */
public final class RuntimeView {
    private final PApplet sketch;
    private RuntimeLayout layout;
    private int layoutWidth, layoutHeight;
    private final List<Label> labels = new ArrayList<>();
    private final Map<String, Label> columnLabels = new LinkedHashMap<>();
    private final Map<RackLocation, Button> buttons = new LinkedHashMap<>();
    private final Map<RackLocation, Button> aisleButtons = new LinkedHashMap<>();
    private final Map<String, Label> aisleColumns = new LinkedHashMap<>();
    private final List<Button> aisleNavigation = new ArrayList<>();
    private final AisleSelection aisle;
    private final Button aisleMode;
    private boolean aisleCooling;
    private Label aisleTitle, aisleMetrics;
    private AisleSelection.Geometry aisleGeometry;
    private final RoomMap room;
    private final PFont font;
    private Label selectionLabel;
    private final RuntimeSession session;
    private final RuntimeClock clock;
    private final RackPanel rackPanel;
    private final CoolingRoomPanel coolingPanel;
    private final RackCoolingPanel rackCoolingPanel;
    private final SummaryPanel summaryPanel;
    private final List<Button> playback = new ArrayList<>();
    private Label statusLabel;

    public RuntimeView(PApplet sketch, RuntimeLayout layout, PFont font, RoomMap room, RuntimeSession session, RuntimeClock clock) {
        this.sketch = sketch;
        this.room = room;
        this.aisle = new AisleSelection(room);
        this.session = session;
        this.clock = clock;
        this.font = font;
        this.layout = layout;
        this.layoutWidth = sketch.width;
        this.layoutHeight = sketch.height;
        float textSize = sketch.height * .018f;
        for (var area : RuntimeLayout.Area.values()) {
            String title = switch (area) {
                case HEADER -> "DATACENTER SIMULATOR / RUNTIME — Cabecera";
                case ROOM -> "Sala";
                case SELECTED_AISLE -> "Pasillo seleccionado";
                case SELECTED_RACK -> "Rack seleccionado";
                case SUMMARY -> "Resumen global";
                case TRENDS -> "Tendencias";
                case ALERTS -> "Alertas";
                case FOOTER -> "Simulación";
            };
            addLabel(area.name() + "Title", title, layout.titleBounds(area),
                    font, textSize * 1.12f, COLOR_BLUE_LABEL);
            String pending = switch (area) {
                    case HEADER -> "Pendiente: contenido y estado de sala.";
                    case ROOM -> "Pendiente: mapa generado desde el layout.";
                    case SELECTED_AISLE -> "Pendiente: selección y detalle\ndel pasillo.";
                    case SELECTED_RACK -> "Pendiente: selección, slots\ny detalle del rack.";
                    case SUMMARY -> "Pendiente: estado global de la sala.";
                    case TRENDS -> "Pendiente: historial y gráficas.";
                    case ALERTS -> "Pendiente: evaluación de alertas.";
                    case FOOTER -> "Pendiente: simulación y acciones.";
                };
            if (area != ROOM && area != FOOTER && area != SELECTED_AISLE && area != SELECTED_RACK && area != SUMMARY) addLabel(area.name() + "Pending", pending, layout.contentBounds(area),
                    font, textSize, COLOR_WHITE_LABEL);
        }
        statusLabel = labels.get(1);
        var geometry = room.geometry(layout, layoutWidth, layoutHeight);
        for (var entry : geometry.columns().entrySet()) {
            int before = labels.size();
            addLabel("Column" + entry.getKey(), entry.getKey(), entry.getValue(), font, textSize * .75f, COLOR_WHITE_LABEL);
            columnLabels.put(entry.getKey(), labels.remove(before));
        }
        for (var placement : geometry.racks()) {
            var rack = placement.rack();
            Button button = new Button(sketch, rack.location().rackCode().value(), placement.bounds());
            button.setStyle(buttonStyle(false, COLOR_EMPTY_RACK));
            button.setTextSize(ControlMeasure.relative(.012f));
            button.setClickListener(() -> select(rack.location()));
            buttons.put(rack.location(), button);
        }
        var content = layout.contentBounds(ROOM).resolve(layoutWidth, layoutHeight);
        addLabel("Selection", "Sin selección", selectionBounds(content), font, textSize * .72f, COLOR_WHITE_LABEL);
        selectionLabel = labels.remove(labels.size() - 1);
        addLabel("AisleName", "", aisleTextBounds(.12f, .12f), font, textSize * .8f, COLOR_WHITE_LABEL);
        aisleTitle = labels.remove(labels.size() - 1);
        addLabel("AisleMetrics", "", aisleTextBounds(.18f, .17f), font, textSize * .72f, COLOR_WHITE_LABEL);
        aisleMetrics = labels.remove(labels.size() - 1);
        for (String column : room.hotAisles().stream().flatMap(a -> a.columns().stream()).distinct().toList()) {
            addLabel("AisleColumn" + column, column, layout.contentBounds(SELECTED_AISLE), font, textSize * .72f, COLOR_WHITE_LABEL);
            aisleColumns.put(column, labels.remove(labels.size() - 1));
        }
        for (var rack : room.racks()) {
            var button = new Button(sketch, rack.location().rackCode().value(), layout.contentBounds(SELECTED_AISLE));
            button.setStyle(buttonStyle(false, COLOR_EMPTY_RACK));
            button.setTextSize(ControlMeasure.relative(.012f));
            button.setClickListener(() -> select(rack.location()));
            button.setVisible(false);
            aisleButtons.put(rack.location(), button);
        }
        for (int i = 0; i < 2; i++) {
            var button = new Button(sketch, i == 0 ? "‹" : "›", aisleNavigationBounds(i));
            button.setStyle(buttonStyle(false, 0xff30363d));
            button.setTextSize(ControlMeasure.relative(.016f));
            aisleNavigation.add(button);
        }
        aisleNavigation.get(0).setClickListener(() -> changeAisle(-1));
        aisleNavigation.get(1).setClickListener(() -> changeAisle(1));
        aisleMode = new Button(sketch, "Refrigeración", aisleModeBounds());
        aisleMode.setStyle(buttonStyle(false, 0xff30363d)); aisleMode.setTextSize(ControlMeasure.relative(.012f));
        aisleMode.setClickListener(() -> { aisleCooling = !aisleCooling; updateAisle(); });
        rackPanel = new RackPanel(sketch, font, room, session, layout);
        coolingPanel = new CoolingRoomPanel(sketch, font, session, layout);
        rackCoolingPanel = new RackCoolingPanel(sketch, font, session, room, layout);
        summaryPanel = new SummaryPanel(sketch, font, layout);
        summaryPanel.refresh(session.snapshot().operationalSnapshot());
        for (int i = 0; i < 3; i++) {
            Button button = new Button(sketch, i == 0 ? "Play" : i == 1 ? "−" : "+", playbackBounds(i));
            button.setStyle(buttonStyle(false, 0xff30363d));
            button.setTextSize(ControlMeasure.relative(.015f));
            playback.add(button);
        }
        playback.get(0).setClickListener(() -> { clock.toggle(); updateStatus(); });
        playback.get(1).setClickListener(() -> { clock.change(-1); updateStatus(); });
        playback.get(2).setClickListener(() -> { clock.change(1); updateStatus(); });
        updateStatus();
        updateRacks();
        updateAisle();
    }

    private com.cpz.processing.controls.controls.geometry.ControlBounds aisleTextBounds(float y, float h) {
        var b = layout.contentBounds(SELECTED_AISLE).resolve(layoutWidth, layoutHeight);
        return com.cpz.processing.controls.controls.geometry.ControlBounds.relative(b.x()/layoutWidth, (b.y()+b.height()*y)/layoutHeight, b.width()/layoutHeight, b.height()*h/layoutHeight);
    }
    private com.cpz.processing.controls.controls.geometry.ControlBounds aisleNavigationBounds(int index) {
        var b = layout.contentBounds(SELECTED_AISLE).resolve(layoutWidth, layoutHeight);
        return com.cpz.processing.controls.controls.geometry.ControlBounds.relative((b.x()+b.width()*(index == 0 ? .15f : .85f))/layoutWidth,
                (b.y()+b.height()*.05f)/layoutHeight, b.width()*.24f/layoutHeight, b.height()*.085f/layoutHeight);
    }
    private com.cpz.processing.controls.controls.geometry.ControlBounds aisleModeBounds() {
        var b = layout.contentBounds(SELECTED_AISLE).resolve(layoutWidth, layoutHeight);
        return com.cpz.processing.controls.controls.geometry.ControlBounds.relative((b.x()+b.width()*.5f)/layoutWidth,
                (b.y()+b.height()*.05f)/layoutHeight, b.width()*.43f/layoutHeight, b.height()*.085f/layoutHeight);
    }
    private void changeAisle(int direction) { aisle.change(direction); updateRacks(); updateAisle(); rackPanel.refresh(); rackCoolingPanel.refresh(); }
    private void updateAisle() {
        var selected = aisle.selected();
        aisleMode.setText(aisleCooling ? "Operación" : "Refrigeración");
        aisleNavigation.get(0).setEnabled(aisle.canPrevious());
        aisleNavigation.get(1).setEnabled(aisle.canNext());
        aisleGeometry = aisle.geometry(layout, layoutWidth, layoutHeight);
        aisleColumns.forEach((code, label) -> {
            var bounds = aisleGeometry.columns().get(code);
            label.setVisible(bounds != null);
            if (bounds != null) label.setBounds(bounds);
        });
        aisleButtons.forEach((location, button) -> {
            var bounds = aisleGeometry.racks().get(location);
            button.setVisible(bounds != null);
            if (bounds != null) {
                button.setBounds(bounds);
                button.setStyle(buttonStyle(location.equals(room.selected()), rackColor(session.snapshot().operationalSnapshot().racks().get(location))));
            }
        });
        if (selected == null) {
            aisleTitle.setText(room.hotAisles().isEmpty() ? "Sin pasillos configurados" : room.selected() == null ? "Seleccione un pasillo" : "Sin pasillo asociado");
            aisleMetrics.setText(aisleCooling ? "Sin pasillo\nSUPPLY/EXHAUST: -- m³/s" : "");
            return;
        }
        aisleTitle.setText(selected.code() + "  " + String.join(", ", selected.columns()));
        var group = session.snapshot().operationalSnapshot().findServerGroup(selected.code()).orElse(null);
        aisleMetrics.setText(aisleCooling ? RuntimeMetrics.aisleText(RuntimeMetrics.aisle(selected,session.coolingZones(),session.snapshot().coolingSnapshot()))
                : AisleMetrics.text(group, session::formatTemperature));
    }

    private com.cpz.processing.controls.controls.geometry.ControlBounds playbackBounds(int index) {
        var b = layout.contentBounds(FOOTER).resolve(layoutWidth, layoutHeight);
        float unit = b.width() / 3;
        return com.cpz.processing.controls.controls.geometry.ControlBounds.relative((b.x() + (index + .5f) * unit) / layoutWidth,
                (b.y() + b.height() * .5f) / layoutHeight, unit * .8f / layoutHeight, b.height() * .85f / layoutHeight);
    }

    private void updateStatus() {
        playback.get(0).setText(clock.running() ? "Pausa" : "Play");
        playback.get(1).setEnabled(clock.canDecrease()); playback.get(2).setEnabled(clock.canIncrease());
        long minutes = (long) (session.snapshot().elapsedSeconds() / 60);
        statusLabel.setText(String.format(java.util.Locale.ROOT, "%s  x%s  %02dd %02d:%02d", clock.running() ? "Running" : "Paused", clock.factor(), minutes / 1440, minutes / 60 % 24, minutes % 60));
    }

    private int rackColor(RackOperationalSnapshot rack) {
        return RackDisplay.color(rack, session.range());
    }
    private void updateRacks() {
        var racks = session.snapshot().operationalSnapshot().racks();
        buttons.forEach((location, button) -> button.setStyle(buttonStyle(location.equals(room.selected()), rackColor(racks.get(location)))));
        updateSelection();
    }
    private void updateSelection() {
        var location = room.selected();
        String selected = "Sin selección";
        if (location != null) {
            var rack = session.snapshot().operationalSnapshot().racks().get(location);
            double temp = RackDisplay.temperature(rack);
            selected = location.column() + " / " + location.rackCode().value() + " " + (Double.isFinite(temp) ? session.formatTemperature(temp) : "--");
        }
        selectionLabel.setText(selected + "\nGris: vacío · azul oscuro: offline · violeta: -- · azul-rojo: "
                + session.formatTemperature(session.range().minServerTemperatureCelsius()) + "–" + session.formatTemperature(session.range().maxServerTemperatureCelsius()));
    }

    private com.cpz.processing.controls.controls.geometry.ControlBounds selectionBounds(com.cpz.processing.controls.controls.geometry.ResolvedBounds b) {
        return com.cpz.processing.controls.controls.geometry.ControlBounds.relative(b.x() / layoutWidth,
                (b.y() + b.height() * .92f) / layoutHeight, b.width() / layoutHeight, b.height() * .08f / layoutHeight);
    }

    private DefaultButtonStyle buttonStyle(boolean selected, int fill) {
        var style = new ButtonStyleConfig();
        style.font = font;
        style.fillOverride = fill;
        style.strokeOverride = selected ? 0xff8ddcff : 0xff82909b;
        style.textOverride = 0xffeeeeee;
        style.strokeWeight = selected ? 2 : 1;
        style.strokeWeightHover = 2;
        return new DefaultButtonStyle(style);
    }

    private void select(RackLocation location) {
        aisle.selectRack(location);
        updateRacks(); updateAisle(); rackPanel.refresh(); rackCoolingPanel.refresh();
    }

    public void mouseEvent(processing.event.MouseEvent event) {
        updateLayoutIfNeeded();
        PointerEvent.Type type = switch (event.getAction()) {
            case processing.event.MouseEvent.PRESS -> PointerEvent.Type.PRESS;
            case processing.event.MouseEvent.RELEASE -> PointerEvent.Type.RELEASE;
            case processing.event.MouseEvent.MOVE -> PointerEvent.Type.MOVE;
            case processing.event.MouseEvent.DRAG -> PointerEvent.Type.DRAG;
            default -> null;
        };
        if (type != null) {
            var pointer = new PointerEvent(type, event.getX(), event.getY());
            boolean wasCooling = coolingPanel.showing();
            coolingPanel.pointer(pointer);
            if (!wasCooling && !coolingPanel.showing()) buttons.values().forEach(button -> button.handlePointerEvent(pointer));
            rackCoolingPanel.pointer(pointer);
            playback.forEach(button -> button.handlePointerEvent(pointer));
            aisleButtons.values().forEach(button -> button.handlePointerEvent(pointer));
            aisleNavigation.forEach(button -> button.handlePointerEvent(pointer));
            aisleMode.handlePointerEvent(pointer);
        }
    }

    private void addLabel(String code, String text, com.cpz.processing.controls.controls.geometry.ControlBounds bounds,
                          PFont font, float size, int color) {
        Label label = new Label(sketch, "runtime" + code, text, bounds);
        LabelStyleConfig style = new LabelStyleConfig();
        style.font = font;
        style.textSize = size;
        style.textColor = color;
        style.alignY = VerticalAlign.TOP;
        style.lineSpacingMultiplier = 1.3f;
        label.setStyle(new DefaultLabelStyle(style));
        label.setTextSize(com.cpz.processing.controls.controls.geometry.ControlMeasure.relative(size / sketch.height));
        labels.add(label);
    }

    private void updateLayoutIfNeeded() {
        if (layoutWidth != sketch.width || layoutHeight != sketch.height) {
            layoutWidth = sketch.width;
            layoutHeight = sketch.height;
            layout = new RuntimeLayout(layoutWidth, layoutHeight);
            int index = 0;
            for (var area : RuntimeLayout.Area.values()) {
                labels.get(index++).setBounds(layout.titleBounds(area));
                if (area != ROOM && area != FOOTER && area != SELECTED_AISLE && area != SELECTED_RACK && area != SUMMARY) labels.get(index++).setBounds(layout.contentBounds(area));
            }
            var geometry = room.geometry(layout, layoutWidth, layoutHeight);
            geometry.columns().forEach((column, bounds) -> columnLabels.get(column).setBounds(bounds));
            geometry.racks().forEach(p -> buttons.get(p.rack().location()).setBounds(p.bounds()));
            selectionLabel.setBounds(selectionBounds(layout.contentBounds(ROOM).resolve(layoutWidth, layoutHeight)));
            for (int i = 0; i < playback.size(); i++) playback.get(i).setBounds(playbackBounds(i));
            aisleTitle.setBounds(aisleTextBounds(.12f, .12f));
            aisleMetrics.setBounds(aisleTextBounds(.18f, .17f));
            for (int i = 0; i < aisleNavigation.size(); i++) aisleNavigation.get(i).setBounds(aisleNavigationBounds(i));
            aisleMode.setBounds(aisleModeBounds());
            updateAisle();
            rackPanel.resize(layout, layoutWidth, layoutHeight);
            coolingPanel.resize(layout, layoutWidth, layoutHeight);
            rackCoolingPanel.resize(layout, layoutWidth, layoutHeight);
            summaryPanel.resize(layout, layoutWidth, layoutHeight);
        }
    }

    public void draw() {
        updateLayoutIfNeeded();
        if (clock.poll()) { session.step(); updateRacks(); updateStatus(); updateAisle(); rackPanel.refresh(); rackCoolingPanel.refresh(); summaryPanel.refresh(session.snapshot().operationalSnapshot()); }
        sketch.background(COLOR_BACKGROUND);
        sketch.pushStyle();
        sketch.rectMode(PApplet.CORNER);
        sketch.strokeWeight(1);
        for (var area : RuntimeLayout.Area.values()) {
            var b = layout.resolve(area, sketch.width, sketch.height);
            sketch.fill(COLOR_EMPTY_RACK);
            sketch.stroke(COLOR_SLOT_OFF);
            sketch.rect(b.x(), b.y(), b.width(), b.height(), layout.spacing(sketch.height) * .4f);
            if (area != HEADER && area != FOOTER) {
                float y = b.y() + layout.spacing(sketch.height) * 2.8f;
                sketch.line(b.x() + layout.spacing(sketch.height), y, b.x() + b.width() - layout.spacing(sketch.height), y);
            }
        }
        sketch.popStyle();
        labels.forEach(Label::draw);
        if (!coolingPanel.showing()) { columnLabels.values().forEach(Label::draw); buttons.values().forEach(Button::draw); selectionLabel.draw(); }
        coolingPanel.draw();
        aisleNavigation.forEach(Button::draw);
        aisleMode.draw();
        aisleTitle.draw(); aisleMetrics.draw();
        aisleColumns.values().forEach(Label::draw);
        aisleButtons.values().forEach(Button::draw);
        if (!rackCoolingPanel.expanded()) rackPanel.draw();
        rackCoolingPanel.draw();
        summaryPanel.draw();
        var selectedAisle = aisle.selected();
        if (selectedAisle != null) session.snapshot().operationalSnapshot().findServerGroup(selectedAisle.code())
                .flatMap(group -> group.maximumTemperatureLocation()).map(location -> location.rackLocation())
                .map(location -> aisleGeometry.racks().get(location)).ifPresent(bounds -> {
                    var b = bounds.resolve(layoutWidth, layoutHeight);
                    sketch.pushStyle(); sketch.fill(0xffffe477); sketch.textAlign(PApplet.RIGHT, PApplet.CENTER);
                    sketch.textSize(layoutHeight * .012f); sketch.text("*", b.x()+b.width()*.44f, b.y()); sketch.popStyle();
                });
        playback.forEach(Button::draw);
    }
}
