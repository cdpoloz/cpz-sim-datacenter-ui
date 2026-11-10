package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.controls.button.config.ButtonStyleConfig;
import com.cpz.processing.controls.controls.button.style.DefaultButtonStyle;
import com.cpz.processing.controls.controls.geometry.ControlBounds;
import com.cpz.processing.controls.controls.geometry.ControlMeasure;
import com.cpz.processing.controls.core.input.PointerEvent;
import processing.core.PApplet;
import processing.core.PFont;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Draws a cached window of recorded ticks, updating only on tick, selection or view changes. */
final class TrendsPanel {
    private static final int WINDOW = 120;
    private static final int[] COLORS = {0xff62b9ff, 0xffffc65c, 0xff79dca5};
    private final PApplet sketch;
    private final RuntimeSession session;
    private final PFont font;
    private final Button mode;
    private RuntimeLayout layout;
    private TrendData.View view = TrendData.View.TEMPERATURE;
    private List<TrendData.Sample> samples = List.of();
    private TrendData.Range range = new TrendData.Range(0, 1);
    private boolean hasValues;
    private long cachedTick = Long.MIN_VALUE;
    private String aisle;

    TrendsPanel(PApplet sketch, PFont font, RuntimeLayout layout, RuntimeSession session) {
        this.sketch = sketch; this.font = font; this.layout = layout; this.session = session;
        mode = new Button(sketch, "Cambiar gráfica ›", buttonBounds(layout, sketch.width, sketch.height));
        var style = new ButtonStyleConfig(); style.font = font; style.fillOverride = 0xff30363d;
        style.strokeOverride = 0xff82909b; style.textOverride = 0xffeeeeee;
        mode.setStyle(new DefaultButtonStyle(style)); mode.setTextSize(ControlMeasure.relative(.012f));
        mode.setClickListener(() -> { view = TrendData.View.values()[(view.ordinal() + 1) % TrendData.View.values().length]; rebuild(); });
    }
    static ControlBounds buttonBounds(RuntimeLayout layout, int width, int height) {
        var b = layout.contentBounds(RuntimeLayout.Area.TRENDS).resolve(width, height);
        return ControlBounds.relative((b.x() + b.width() * .84f) / width, (b.y() + b.height() * .09f) / height,
                b.width() * .30f / height, b.height() * .17f / height);
    }
    void resize(RuntimeLayout layout, int width, int height) { this.layout = layout; mode.setBounds(buttonBounds(layout, width, height)); }
    void pointer(PointerEvent event) { mode.handlePointerEvent(event); }
    void refresh(String selectedAisle) {
        long tick = session.snapshot().tickIndex();
        if (tick != cachedTick || !Objects.equals(aisle, selectedAisle)) {
            aisle = selectedAisle; rebuild();
        }
    }
    private void rebuild() {
        cachedTick = session.snapshot().tickIndex();
        samples = TrendData.project(session.recentHistory(WINDOW), view, aisle);
        range = TrendData.range(samples);
        hasValues = samples.stream().anyMatch(s -> Double.isFinite(s.first()) || Double.isFinite(s.second()) || Double.isFinite(s.third()));
    }
    void draw() {
        var b = layout.contentBounds(RuntimeLayout.Area.TRENDS).resolve(sketch.width, sketch.height);
        float x = b.x(), y = b.y(), w = b.width(), h = b.height();
        float left = x + w * .11f, right = x + w * .98f, top = y + h * .40f, bottom = y + h * .84f;
        sketch.pushStyle(); if (font != null) sketch.textFont(font); sketch.textSize(sketch.height * .012f);
        sketch.textAlign(PApplet.LEFT, PApplet.TOP); sketch.fill(0xffeeeeee);
        String title = switch (view) {
            case TEMPERATURE -> "Temperaturas · °C";
            case AISLE -> "Pasillo " + (aisle == null ? "sin selección" : aisle) + " · °C";
            case UTILIZATION -> "Utilización IT online · %";
            case POWER -> "Potencia eléctrica · kW";
        };
        sketch.text(title, x, y);
        String[] legends = switch (view) {
            case TEMPERATURE -> new String[]{"Serv. online", "Rack más caliente (media)", "Aire sala"};
            case AISLE -> new String[]{"Media servidores online"};
            case UTILIZATION -> new String[]{"IT online"};
            case POWER -> new String[]{"IT", "Instalación"};
        };
        float legendX = x;
        for (int i = 0; i < legends.length; i++) {
            sketch.fill(COLORS[i]); String legend = "● " + legends[i]; sketch.text(legend, legendX, y + h * .22f);
            legendX += sketch.textWidth(legend) + sketch.height * .014f;
        }
        sketch.stroke(0xff82909b); sketch.noFill(); sketch.rectMode(PApplet.CORNERS); sketch.rect(left, top, right, bottom);
        sketch.fill(0xffeeeeee); sketch.textAlign(PApplet.RIGHT, PApplet.CENTER);
        sketch.text(String.format(Locale.ROOT, "%.2f", range.max()), left - 4, top);
        sketch.text(String.format(Locale.ROOT, "%.2f", range.min()), left - 4, bottom);
        if (samples.isEmpty()) {
            sketch.textAlign(PApplet.CENTER, PApplet.CENTER); sketch.text("Sin historial", (left + right) / 2, (top + bottom) / 2);
        } else {
            long first = samples.getFirst().tick(), last = samples.getLast().tick();
            sketch.textAlign(PApplet.LEFT, PApplet.TOP); sketch.text("tick " + first, left, bottom + 3);
            sketch.textAlign(PApplet.RIGHT, PApplet.TOP); sketch.text("tick " + last, right, bottom + 3);
            for (int series = 0; series < legends.length; series++) drawSeries(series, first, last, left, right, top, bottom);
            if (!hasValues) {
                sketch.textAlign(PApplet.CENTER, PApplet.CENTER); sketch.text("Sin datos", (left + right) / 2, (top + bottom) / 2);
            }
        }
        sketch.popStyle(); mode.draw();
    }
    private void drawSeries(int series, long first, long last, float left, float right, float top, float bottom) {
        sketch.stroke(COLORS[series]); sketch.strokeWeight(2);
        boolean previous = false; float px = 0, py = 0;
        for (var sample : samples) {
            double value = series == 0 ? sample.first() : series == 1 ? sample.second() : sample.third();
            if (!Double.isFinite(value)) { previous = false; continue; }
            float x = (float)(left + TrendData.xFraction(sample.tick(), first, last) * (right - left));
            float y = (float)(bottom - range.fraction(value) * (bottom - top));
            if (previous) sketch.line(px, py, x, y); else sketch.point(x, y);
            px = x; py = y; previous = true;
        }
    }
}
