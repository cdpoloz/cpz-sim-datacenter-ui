package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.label.config.LabelStyleConfig;
import com.cpz.processing.controls.controls.label.style.DefaultLabelStyle;
import com.cpz.processing.controls.controls.label.style.VerticalAlign;
import processing.core.PApplet;
import processing.core.PFont;

import java.util.ArrayList;
import java.util.List;

import static com.cpz.sim.datacenter.ui.runtime.RuntimeLayout.Area.*;
import static com.cpz.sim.datacenter.ui.util.Constants.*;

/** Composes public, non-interactive Label facades and draws the decorative shell behind them. */
public final class RuntimeView {
    private final PApplet sketch;
    private RuntimeLayout layout;
    private int layoutWidth, layoutHeight;
    private final List<Label> labels = new ArrayList<>();

    public RuntimeView(PApplet sketch, RuntimeLayout layout, PFont font) {
        this.sketch = sketch;
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
            addLabel(area.name() + "Pending", pending, layout.contentBounds(area),
                    font, textSize, COLOR_WHITE_LABEL);
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
                labels.get(index++).setBounds(layout.contentBounds(area));
            }
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
    }
}
