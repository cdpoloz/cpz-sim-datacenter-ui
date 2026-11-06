package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.controls.button.config.ButtonStyleConfig;
import com.cpz.processing.controls.controls.button.style.DefaultButtonStyle;
import com.cpz.processing.controls.controls.geometry.ControlBounds;
import com.cpz.processing.controls.controls.geometry.ControlMeasure;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.label.config.LabelStyleConfig;
import com.cpz.processing.controls.controls.label.style.DefaultLabelStyle;
import com.cpz.processing.controls.controls.label.style.VerticalAlign;
import com.cpz.processing.controls.core.input.PointerEvent;
import processing.core.PApplet;
import processing.core.PFont;
import java.util.Locale;
import static com.cpz.sim.datacenter.ui.runtime.RuntimeLayout.Area.SELECTED_RACK;
import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_WHITE_LABEL;

/** Expandable, non-physical projection of the selected rack's installed unit. */
final class RackCoolingPanel {
    private final RuntimeSession session;
    private final RoomMap room;
    private final Button tab, toggle;
    private final Label detail;
    private RuntimeLayout layout;
    private int width, height;
    private boolean expanded;
    RackCoolingPanel(PApplet sketch, PFont font, RuntimeSession session, RoomMap room, RuntimeLayout layout) {
        this.session = session; this.room = room; this.layout = layout; width = sketch.width; height = sketch.height;
        var style = new ButtonStyleConfig(); style.font = font; style.fillOverride = 0xff30363d; style.strokeOverride = 0xff82909b;
        style.textOverride = 0xffeeeeee; style.strokeWeight = 1; style.strokeWeightHover = 2;
        tab = new Button(sketch, "Frío", bounds(.8f,.035f,.35f,.06f)); tab.setStyle(new DefaultButtonStyle(style)); tab.setTextSize(ControlMeasure.relative(.011f));
        toggle = new Button(sketch, "", bounds(.5f,.16f,.8f,.075f)); toggle.setStyle(new DefaultButtonStyle(style)); toggle.setTextSize(ControlMeasure.relative(.013f));
        tab.setClickListener(() -> { expanded = !expanded; refresh(); });
        toggle.setClickListener(() -> { var rack = room.selected(); session.rackCooling(rack).ifPresent(unit -> session.setRackCooling(rack, !unit.enabled())); refresh(); });
        detail = new Label(sketch, "runtimeRackCoolingDetail", "", layout.contentBounds(SELECTED_RACK));
        var labelStyle = new LabelStyleConfig(); labelStyle.font = font; labelStyle.textColor = COLOR_WHITE_LABEL;
        labelStyle.alignY = VerticalAlign.TOP; labelStyle.lineSpacingMultiplier = 1.3f;
        detail.setStyle(new DefaultLabelStyle(labelStyle)); detail.setTextSize(ControlMeasure.relative(.013f));
        resize(layout,width,height); refresh();
    }
    private ControlBounds bounds(float x,float y,float w,float h) {
        var b=layout.contentBounds(SELECTED_RACK).resolve(width,height);
        return ControlBounds.relative((b.x()+b.width()*x)/width,(b.y()+b.height()*y)/height,b.width()*w/height,b.height()*h/height);
    }
    void resize(RuntimeLayout layout,int width,int height) {
        this.layout=layout;this.width=width;this.height=height;
        tab.setBounds(bounds(.80f,.035f,.35f,.06f)); toggle.setBounds(bounds(.5f,.16f,.8f,.075f));
        var b=layout.contentBounds(SELECTED_RACK).resolve(width,height);
        detail.setBounds(ControlBounds.relative(b.x()/width,(b.y()+b.height()*.25f)/height,b.width()/height,b.height()*.7f/height));
    }
    boolean expanded() { return expanded; }
    void refresh() {
        tab.setText(expanded ? "Slots" : "Frío");
        var rack = room.selected(); var unit = session.rackCooling(rack);
        toggle.setVisible(expanded && unit.isPresent());
        if (rack == null) detail.setText("Seleccione un rack");
        else if (!session.rackCoolingAvailable()) detail.setText("Refrigeración integrada\nno disponible en esta ruta");
        else if (unit.isEmpty()) detail.setText(rack.column()+" / "+rack.rackCode().value()+"\nSin unidad integrada");
        else {
            var value = unit.orElseThrow(); var u=value.definition();
            toggle.setText("Unidad " + (value.enabled() ? "ON" : "OFF"));
            String air = value.lastTick().map(s -> format("%.1f °C",s.airTemperatureCelsius())).orElse("--");
            String heat = value.lastTick().map(s -> format("%.2f kW",s.actualCoolingPowerWatts()/1000)).orElse("--");
            detail.setText(rack.column()+" / "+rack.rackCode().value()+"\n"+u.code()+"\nDestino: "+u.heatRejectionDestination()
                    +"\nCapacidad nominal: "+format("%.2f kW",u.ratedCoolingCapacityWatts()/1000)
                    +"\nEléctrica actual: "+format("%.2f kW",value.electricalWatts()/1000)
                    +"\nÚltimo tick — aire: "+air+"\nÚltimo tick — extraída: "+heat);
        }
    }
    private static String format(String f,double d) { return Double.isFinite(d) ? String.format(Locale.ROOT,f,d) : "--"; }
    void pointer(PointerEvent event) { tab.handlePointerEvent(event); if (expanded) toggle.handlePointerEvent(event); }
    void draw() { if (expanded) { detail.draw(); toggle.draw(); } tab.draw(); }
}
