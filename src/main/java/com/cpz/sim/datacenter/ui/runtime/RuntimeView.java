package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.controls.button.config.ButtonStyleConfig;
import com.cpz.processing.controls.controls.button.style.DefaultButtonStyle;
import com.cpz.processing.controls.controls.geometry.ControlMeasure;
import com.cpz.processing.controls.core.input.PointerEvent;
import com.cpz.sim.datacenter.model.RackLocation;
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
    private final RoomMap room;
    private final PFont font;
    private Label selectionLabel;

    public RuntimeView(PApplet sketch, RuntimeLayout layout, PFont font, RoomMap room) {
        this.sketch = sketch;
        this.room = room;
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
                case FOOTER -> "Pie / Hito 1 — Estructura visual";
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
            if (area != ROOM) addLabel(area.name() + "Pending", pending, layout.contentBounds(area),
                    font, textSize, COLOR_WHITE_LABEL);
        }
        var geometry = room.geometry(layout, layoutWidth, layoutHeight);
        for (var entry : geometry.columns().entrySet()) {
            int before = labels.size();
            addLabel("Column" + entry.getKey(), entry.getKey(), entry.getValue(), font, textSize * .75f, COLOR_WHITE_LABEL);
            columnLabels.put(entry.getKey(), labels.remove(before));
        }
        for (var placement : geometry.racks()) {
            var rack = placement.rack();
            Button button = new Button(sketch, rack.location().rackCode().value(), placement.bounds());
            button.setStyle(buttonStyle(false));
            button.setTextSize(ControlMeasure.relative(.012f));
            button.setClickListener(() -> select(rack.location()));
            buttons.put(rack.location(), button);
        }
        var content = layout.contentBounds(ROOM).resolve(layoutWidth, layoutHeight);
        addLabel("Selection", "Sin selección", selectionBounds(content), font, textSize * .72f, COLOR_WHITE_LABEL);
        selectionLabel = labels.remove(labels.size() - 1);
    }

    private com.cpz.processing.controls.controls.geometry.ControlBounds selectionBounds(com.cpz.processing.controls.controls.geometry.ResolvedBounds b) {
        return com.cpz.processing.controls.controls.geometry.ControlBounds.relative(b.x() / layoutWidth,
                (b.y() + b.height() * .92f) / layoutHeight, b.width() / layoutHeight, b.height() * .08f / layoutHeight);
    }

    private DefaultButtonStyle buttonStyle(boolean selected) {
        var style = new ButtonStyleConfig();
        style.font = font;
        style.fillOverride = selected ? 0xff315b79 : 0xff30363d;
        style.strokeOverride = selected ? 0xff8ddcff : 0xff82909b;
        style.textOverride = 0xffeeeeee;
        style.strokeWeight = selected ? 2 : 1;
        style.strokeWeightHover = 2;
        return new DefaultButtonStyle(style);
    }

    private void select(RackLocation location) {
        var previous = room.selected();
        room.select(location);
        if (previous != null) buttons.get(previous).setStyle(buttonStyle(false));
        buttons.get(location).setStyle(buttonStyle(true));
        selectionLabel.setText("Seleccionado: " + location.column() + " / " + location.rackCode().value());
    }

    public void mouseEvent(processing.event.MouseEvent event) {
        PointerEvent.Type type = switch (event.getAction()) {
            case processing.event.MouseEvent.PRESS -> PointerEvent.Type.PRESS;
            case processing.event.MouseEvent.RELEASE -> PointerEvent.Type.RELEASE;
            case processing.event.MouseEvent.MOVE -> PointerEvent.Type.MOVE;
            case processing.event.MouseEvent.DRAG -> PointerEvent.Type.DRAG;
            default -> null;
        };
        if (type != null) {
            var pointer = new PointerEvent(type, event.getX(), event.getY());
            buttons.values().forEach(button -> button.handlePointerEvent(pointer));
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

    public void draw() {
        if (layoutWidth != sketch.width || layoutHeight != sketch.height) {
            layoutWidth = sketch.width;
            layoutHeight = sketch.height;
            layout = new RuntimeLayout(layoutWidth, layoutHeight);
            int index = 0;
            for (var area : RuntimeLayout.Area.values()) {
                labels.get(index++).setBounds(layout.titleBounds(area));
                if (area != ROOM) labels.get(index++).setBounds(layout.contentBounds(area));
            }
            var geometry = room.geometry(layout, layoutWidth, layoutHeight);
            geometry.columns().forEach((column, bounds) -> columnLabels.get(column).setBounds(bounds));
            geometry.racks().forEach(p -> buttons.get(p.rack().location()).setBounds(p.bounds()));
            selectionLabel.setBounds(selectionBounds(layout.contentBounds(ROOM).resolve(layoutWidth, layoutHeight)));
        }
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
        columnLabels.values().forEach(Label::draw);
        buttons.values().forEach(Button::draw);
        selectionLabel.draw();
    }
}
