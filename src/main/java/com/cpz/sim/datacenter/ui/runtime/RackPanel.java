package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.geometry.ControlBounds;
import com.cpz.processing.controls.controls.geometry.ControlMeasure;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.label.config.LabelStyleConfig;
import com.cpz.processing.controls.controls.label.style.DefaultLabelStyle;
import com.cpz.processing.controls.controls.label.style.VerticalAlign;
import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackLocation;
import com.cpz.utils.color.Colors;
import processing.core.PApplet;
import processing.core.PFont;
import java.util.*;
import static com.cpz.sim.datacenter.ui.util.Constants.*;

/** Selected-rack labels are recreated only when the selected rack changes. */
final class RackPanel {
    record Geometry(ControlBounds title, ControlBounds summary, ControlBounds columns, ControlBounds legend, List<ControlBounds> rows, List<ControlBounds> indicators) { }
    private static final int INDICATOR_BORDER = 0xffd5dce3;
    private static final int MISSING_BORDER = 0xffffd166;
    private final PApplet sketch;
    private final PFont font;
    private final RoomMap room;
    private final RuntimeSession session;
    private final Label title, summary, columns, legend;
    private final List<Label> rows = new ArrayList<>();
    private final List<Indicator> indicators = new ArrayList<>();
    private RackLocation current;
    private RuntimeLayout layout;
    private int width, height;

    RackPanel(PApplet sketch, PFont font, RoomMap room, RuntimeSession session, RuntimeLayout layout) {
        this.sketch = sketch; this.font = font; this.room = room; this.session = session;
        this.layout = layout; width = sketch.width; height = sketch.height;
        title = label("rackTitle", .014f); summary = label("rackSummary", .011f);
        columns = label("rackColumns", .010f); legend = label("rackLegend", .009f);
        refresh();
    }
    private Label label(String code, float size) {
        var label = new Label(sketch, code, "", layout.contentBounds(RuntimeLayout.Area.SELECTED_RACK));
        var style = new LabelStyleConfig(); style.font = font; style.textColor = COLOR_WHITE_LABEL;
        style.alignY = VerticalAlign.TOP; style.lineSpacingMultiplier = 1.15f;
        label.setStyle(new DefaultLabelStyle(style)); label.setTextSize(ControlMeasure.relative(size));
        return label;
    }
    static Geometry geometry(RuntimeLayout layout, int width, int height, int count) {
        var b = layout.contentBounds(RuntimeLayout.Area.SELECTED_RACK).resolve(width, height);
        var rows = new ArrayList<ControlBounds>();
        var indicators = new ArrayList<ControlBounds>();
        float rowHeight = count == 0 ? 0 : b.height() * .55f / count;
        float diameter = Math.min(height * .010f, rowHeight * .58f);
        float indent = Math.min(b.width() * .12f, diameter + height * .004f);
        for (int i = 0; i < count; i++) {
            float y = b.y()+b.height()*.30f+i*rowHeight;
            rows.add(bounds(b.x()+indent, y, b.width()-indent, rowHeight, width, height));
            indicators.add(bounds(b.x(), y+(rowHeight-diameter)*.5f, diameter, diameter, width, height));
        }
        return new Geometry(bounds(b.x(), b.y(), b.width(), b.height()*.06f, width, height),
                bounds(b.x(), b.y()+b.height()*.07f, b.width(), b.height()*.15f, width, height),
                bounds(b.x()+indent, b.y()+b.height()*.24f, b.width()-indent, b.height()*.05f, width, height),
                bounds(b.x(), b.y()+b.height()*.85f, b.width(), b.height()*.15f, width, height), List.copyOf(rows), List.copyOf(indicators));
    }
    private static ControlBounds bounds(float x, float y, float w, float h, int width, int height) {
        return ControlBounds.relative(x/width, y/height, w/height, h/height);
    }
    void resize(RuntimeLayout layout, int width, int height) { this.layout = layout; this.width = width; this.height = height; position(); }
    private void position() {
        var g = geometry(layout, width, height, rows.size());
        title.setBounds(g.title()); summary.setBounds(g.summary()); columns.setBounds(g.columns()); legend.setBounds(g.legend());
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setBounds(g.rows().get(i));
            indicators.get(i).setBounds(g.indicators().get(i));
            rows.get(i).setTextSize(ControlMeasure.relative(Math.min(.0105f, g.rows().get(i).resolve(width, height).height() / height * .68f)));
        }
    }
    void refresh() {
        RackLocation selected = room.selected();
        if (!Objects.equals(current, selected)) { rows.clear(); indicators.clear(); current = selected; }
        if (selected == null) {
            title.setText("Seleccione un rack"); summary.setText(""); columns.setText(""); legend.setText(""); position(); return;
        }
        var rack = room.racks().stream().filter(r -> r.location().equals(selected)).findFirst().orElseThrow();
        var data = RackDetail.project(rack, session.serverRoles(), session.snapshot());
        while (rows.size() < data.rows().size()) {
            int index = rows.size();
            rows.add(label("rackSlot" + index, .0105f));
            var indicator = new Indicator(sketch, "rackSlotIndicator" + index, layout.contentBounds(RuntimeLayout.Area.SELECTED_RACK));
            indicator.setOn(true); indicator.setStrokeWeight(1f); indicators.add(indicator);
        }
        title.setText(selected.column() + " / " + selected.rackCode().value());
        var s = data.summary();
        summary.setText(s == null ? "Resumen: datos no disponibles" : "Media online: " + (s.hasOnlineServers() ? temp(s.averageOnlineTemperatureCelsius()) : "--") + "  " + (s.hasOnlineServers() ? percent(s.averageOnlineUtilization()) : "--")
                + "\nPotencia total: " + power(s.currentPowerWatts()));
        columns.setText(data.rows().isEmpty() ? "Sin slots" : "SLOT   TEMP    CARGA   POT.    ESTADO / ROL");
        for (int i = 0; i < data.rows().size(); i++) {
            var row = data.rows().get(i); var label = rows.get(i);
            var indicator = indicators.get(i);
            indicator.setOnColor(indicatorColor(row, session.range()));
            indicator.setStrokeColor(missingTemperature(row) ? MISSING_BORDER : INDICATOR_BORDER);
            label.setTextColor(COLOR_WHITE_LABEL);
            if (!row.installed()) { label.setText(row.location().slot() + "   --     --      --      VACÍO"); continue; }
            double t = row.temperature() == null ? Double.NaN : row.temperature().temperatureCelsius();
            double load = row.energy() == null ? Double.NaN : row.energy().utilization();
            double watts = row.energy() == null ? Double.NaN : row.energy().currentPowerWatts();
            String state = row.health() == null ? "SIN DATOS" : row.health().status().name();
            String reasons = row.health() == null ? "" : row.health().alertReasons().stream().sorted().map(RackPanel::reason).reduce("", (a,b) -> a.isEmpty() ? b : a + "," + b);
            boolean missing = row.incomplete() || !Double.isFinite(t) || !Double.isFinite(load) || !Double.isFinite(watts);
            label.setText(String.format(Locale.ROOT, "%-5s %6s %5s %6s %s/%s%s%s", row.location().slot(), temp(t), percent(load), power(watts), state, role(row.role()), reasons.isEmpty() ? "" : " " + reasons, missing ? " ?" : ""));
        }
        legend.setText("Gris: vacío; azul oscuro: offline; borde amarillo: sin temp\nU: carga alta; T: temp alta; ?: faltan datos\nGP: general; AI: IA; ST: almacén; DB: base datos\nEDGE: borde; GPU: gráfico; MGMT: gestión\n" + RackDetail.provenance(session.definition()));
        position();
    }
    private static boolean missingTemperature(RackDetail.Row row) {
        return row.installed() && (row.temperature() == null || !Double.isFinite(row.temperature().temperatureCelsius()));
    }
    static int indicatorColor(RackDetail.Row row, com.cpz.sim.datacenter.ui.simulation.TemperatureRange range) {
        if (!row.installed()) return COLOR_EMPTY_RACK;
        if (missingTemperature(row)) return RackDisplay.UNAVAILABLE;
        if (row.health() != null && row.health().status() == HardwareStatus.OFFLINE) return RackDisplay.OFFLINE;
        double t = row.temperature().temperatureCelsius();
        float min = range.minServerTemperatureCelsius(), max = range.maxServerTemperatureCelsius();
        return Colors.lerpColor(COLOR_MIN_TEMPERATURE, COLOR_MAX_TEMPERATURE, max > min ? Math.clamp((float)((t-min)/(max-min)), 0, 1) : 0);
    }
    private String temp(double value) { return Double.isFinite(value) ? session.formatTemperature(value) : "--"; }
    private String percent(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.0f%%", value * 100) : "--"; }
    private String power(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.0fW", value) : "--"; }
    private static String role(com.cpz.sim.datacenter.model.ServerRole role) { return switch(role) {
        case GENERAL_PURPOSE -> "GP"; case AI -> "AI"; case STORAGE -> "ST"; case DATABASE -> "DB";
        case EDGE -> "EDGE"; case GPU -> "GPU"; case MANAGEMENT -> "MGMT";
    }; }
    private static String reason(ServerAlertReason reason) { return switch(reason) {
        case HIGH_UTILIZATION -> "U"; case HIGH_TEMPERATURE -> "T"; default -> reason.name();
    }; }
    void draw() { title.draw(); summary.draw(); columns.draw(); indicators.forEach(Indicator::draw); rows.forEach(Label::draw); legend.draw(); }
}
