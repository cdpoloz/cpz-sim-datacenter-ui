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
import com.cpz.sim.datacenter.cooling.CoolingUnitType;
import processing.core.PApplet;
import processing.core.PFont;
import java.util.*;
import static com.cpz.sim.datacenter.ui.runtime.RuntimeLayout.Area.ROOM;
import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_WHITE_LABEL;

/** Local page of controls for the real cooling units; no simulation scheduling. */
final class CoolingRoomPanel {
    private static final int PAGE_SIZE = 8;
    private final RuntimeSession session;
    private final PApplet sketch;
    private final PFont font;
    private final List<RuntimeSession.Unit> units;
    private final List<Button> unitButtons = new ArrayList<>();
    private final List<Button> masters = new ArrayList<>(), navigation = new ArrayList<>();
    private final Button tab;
    private final Label help;
    private RuntimeLayout layout;
    private int width, height, page;
    private boolean visible;

    CoolingRoomPanel(PApplet sketch, PFont font, RuntimeSession session, RuntimeLayout layout) {
        this.sketch = sketch; this.font = font; this.session = session; this.layout = layout;
        width = sketch.width; height = sketch.height; units = session.coolingUnits();
        tab = button("Refrigeración", rect(.8f, .0f, .35f, .06f, true));
        tab.setClickListener(() -> { visible = !visible; refresh(); });
        for (var type : List.of(CoolingUnitType.SUPPLY, CoolingUnitType.EXHAUST)) {
            var button = button("", rect(.5f, .1f, .3f, .07f, false));
            button.setClickListener(() -> { session.setCoolingGroup(type, session.coolingActive(type) == 0); refresh(); });
            masters.add(button);
        }
        for (var unit : units) {
            var button = button("", rect(.5f, .3f, .8f, .07f, false));
            button.setClickListener(() -> { session.setCoolingEnabled(unit.code(), !session.coolingEnabled(unit.code())); refresh(); });
            unitButtons.add(button);
        }
        for (int direction : new int[]{-1, 1}) {
            var button = button(direction < 0 ? "‹" : "›", rect(.5f, .9f, .2f, .06f, false));
            button.setClickListener(() -> { page = Math.clamp(page + direction, 0, maxPage()); refresh(); });
            navigation.add(button);
        }
        help = new Label(sketch, "coolingHelp", "", layout.contentBounds(ROOM));
        var style = new LabelStyleConfig(); style.font = font; style.textColor = COLOR_WHITE_LABEL; style.alignY = VerticalAlign.TOP;
        help.setStyle(new DefaultLabelStyle(style)); help.setTextSize(ControlMeasure.relative(.013f));
        resize(layout, width, height); refresh();
    }
    private Button button(String text, ControlBounds bounds) {
        var button = new Button(sketch, text, bounds); var style = new ButtonStyleConfig();
        style.font = font; style.fillOverride = 0xff30363d; style.strokeOverride = 0xff82909b; style.textOverride = 0xffeeeeee;
        style.strokeWeight = 1; style.strokeWeightHover = 2; button.setStyle(new DefaultButtonStyle(style));
        button.setTextSize(ControlMeasure.relative(.014f)); return button;
    }
    // Buttons use centre coordinates. All dimensions are relative to surface height.
    private ControlBounds rect(float x, float y, float w, float h, boolean title) {
        var b = (title ? layout.titleBounds(ROOM) : layout.contentBounds(ROOM)).resolve(width, height);
        return ControlBounds.relative((b.x()+b.width()*x)/width, (b.y()+b.height()*y)/height, b.width()*w/height, b.height()*h/height);
    }
    void resize(RuntimeLayout layout, int width, int height) {
        this.layout = layout; this.width = width; this.height = height;
        tab.setBounds(rect(.80f, .45f, .35f, .75f, true));
        for (int i=0;i<2;i++) masters.get(i).setBounds(rect(i == 0 ? .25f : .75f, .13f, .46f, .085f, false));
        for (int i=0;i<unitButtons.size();i++) unitButtons.get(i).setBounds(rect(.5f, .28f+(i%PAGE_SIZE)*.072f, .9f, .064f, false));
        navigation.get(0).setBounds(rect(.25f,.94f,.35f,.075f,false));
        navigation.get(1).setBounds(rect(.75f,.94f,.35f,.075f,false));
        var b = layout.contentBounds(ROOM).resolve(width, height);
        help.setBounds(ControlBounds.relative(b.x()/width, (b.y()+b.height()*.015f)/height, b.width()/height, b.height()*.065f/height));
    }
    private int maxPage() { return Math.max(0, (units.size()-1)/PAGE_SIZE); }
    boolean showing() { return visible; }
    void refresh() {
        tab.setText(visible ? "Mapa" : "Refrigeración");
        help.setText("Maestro: ON si alguna está activa · Página " + (page+1) + "/" + (maxPage()+1));
        help.setVisible(visible);
        for (int i=0;i<2;i++) {
            var type = i == 0 ? CoolingUnitType.SUPPLY : CoolingUnitType.EXHAUST;
            long total = units.stream().filter(u -> u.type() == type).count(); long active = session.coolingActive(type);
            masters.get(i).setText(type + " " + active + "/" + total + (total == 0 ? " sin unidades" : active > 0 ? " ON" : " OFF"));
            masters.get(i).setEnabled(total > 0); masters.get(i).setVisible(visible);
        }
        for (int i=0;i<units.size();i++) {
            var unit = units.get(i); var button = unitButtons.get(i);
            button.setText(unit.type() + "  " + unit.code() + "  " + (session.coolingEnabled(unit.code()) ? "ON" : "OFF"));
            button.setVisible(visible && i/PAGE_SIZE == page);
        }
        navigation.get(0).setEnabled(page > 0); navigation.get(1).setEnabled(page < maxPage());
        navigation.forEach(b -> b.setVisible(visible));
    }
    void pointer(PointerEvent event) {
        tab.handlePointerEvent(event);
        if (!visible) return;
        masters.forEach(b -> b.handlePointerEvent(event));
        unitButtons.forEach(b -> b.handlePointerEvent(event));
        navigation.forEach(b -> b.handlePointerEvent(event));
    }
    void draw() { tab.draw(); if (visible) { help.draw(); masters.forEach(Button::draw); unitButtons.forEach(Button::draw); navigation.forEach(Button::draw); } }
}
