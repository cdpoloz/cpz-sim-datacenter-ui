package com.cpz.sim.datacenter.ui.main;

import com.cpz.sim.datacenter.ui.dataset.DatasetReplayController;
import com.cpz.sim.datacenter.model.ServerLocation;
import processing.core.PApplet;
import processing.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Logical inventory browser. Processing owns load, selection, pulses and restart on one thread. */
public final class DatasetReplaySketch extends PApplet {
    private DatasetReplayController controller;
    private int rackPage, serverPage, errorScroll;
    private final List<Runnable> actions = new ArrayList<>();
    private final List<float[]> bounds = new ArrayList<>();
    private static final int INK = 0xffe1e9f3, MUTED = 0xff9aaec5, ACCENT = 0xff54dec0;

    @Override public void settings() { size(1440, 940); }
    @Override public void setup() {
        surface.setTitle("Pyxis | Dataset replay");
        textFont(createFont("SansSerif", 15));
        frameRate(30);
        // Synchronous preparation: controls become available only after full backend validation.
        controller = DatasetReplayController.load(System.getProperties());
    }
    @Override public void draw() {
        controller.pulse(System.nanoTime());
        actions.clear(); bounds.clear();
        background(0xff101925);
        textSize(26); label("PYXIS / Dataset replay", 28, 40, INK);
        textSize(15);
        label("Logical inventory • no physical dimensions", 28, 68, MUTED);
        label("State: " + controller.state(), 1120, 40, ACCENT);
        controller.session().ifPresent(session -> {
            var m = session.dataset().metadata();
            boolean synthetic = "synthetic-20-rack-pilot".equals(m.get("datasetId"))
                    && "SyntheticDatasetGenerator v1".equals(m.get("sourceName"))
                    && "synthetic-dataset-pilot".equals(m.get("configurationId"));
            label((synthetic ? "Synthetic | " : "") + "Dataset: " + value(m.get("datasetId"))
                    + " | Source: " + value(m.get("sourceName")), 28, 102, INK);
            label("Configuration: " + value(m.get("configurationId")) + " / " + value(m.get("configurationVersion"))
                    + " | " + session.plan().mode(), 28, 128, INK);
            label("Coverage [" + value(m.get("coverageStartUtc")) + ", " + value(m.get("coverageEndExclusiveUtc")) + ")", 28, 154, MUTED);
            label("Cooling: not configured", 1120, 102, MUTED);
            label("Query step: " + session.plan().step() + " | Points: " + session.plan().points()
                    + " | Observation cadence: " + value(m.get("nominalSamplingIntervalSeconds")) + " s", 28, 180, MUTED);
        });
        boolean playable = controller.state() == DatasetReplayController.State.READY || controller.state() == DatasetReplayController.State.PAUSED;
        boolean running = controller.state() == DatasetReplayController.State.RUNNING;
        button(running ? "Pause" : "Play", 28, 200, 110, running || playable,
                () -> { if (running) controller.pause(); else controller.play(System.nanoTime()); });
        button("Restart", 150, 200, 110, controller.session().isPresent() && controller.state() != DatasetReplayController.State.INVALID,
                () -> { controller.restart(); errorScroll = 0; });
        button("Slower", 286, 200, 100, true, () -> controller.speed(Math.max(.25, controller.stepsPerSecond()/2), System.nanoTime()));
        label(String.format(Locale.ROOT, "%.2f steps/s", controller.stepsPerSecond()), 402, 225, ACCENT);
        button("Faster", 520, 200, 100, true, () -> controller.speed(Math.min(8, controller.stepsPerSecond()*2), System.nanoTime()));
        label("Progress: " + controller.history().size() + " completed points", 660, 225, MUTED);
        if (controller.state() == DatasetReplayController.State.INVALID) { errors(28, 265, 1380, 620); return; }
        racks(); servers(); comparison();
        if (controller.failure().isPresent()) errors(680, 640, 730, 250);
        label("Selection reads the last completed frame. Speed changes wall pulses only. Restart keeps the captured inputs.", 28, 920, MUTED);
    }
    private void racks() {
        panel(28, 260, 400, 625);
        label("RACKS / " + controller.racks().size(), 44, 290, ACCENT);
        var racks = controller.racks();
        for (int i = rackPage * 20; i < Math.min(racks.size(), rackPage * 20 + 20); i++) {
            var rack = racks.get(i);
            int local = i % 20;
            float x = 42 + (local / 10) * 190, y = 310 + (local % 10) * 50;
            boolean selected = rack.location().equals(controller.selectedRack());
            button(rack.location().column() + " / " + rack.location().rackCode().value() + "  " + value(rack.row()),
                    x, y, 180, true, () -> { controller.selectRack(rack.location()); serverPage = 0; }, selected);
        }
        button("Previous", 42, 835, 110, rackPage > 0, () -> rackPage--);
        button("Next", 294, 835, 110, (rackPage+1)*20 < racks.size(), () -> rackPage++);
    }
    private void servers() {
        panel(444, 260, 216, 625);
        var servers = controller.servers();
        label("SERVERS / " + servers.size(), 460, 290, ACCENT);
        for (int i = serverPage*10; i < Math.min(servers.size(), serverPage*10+10); i++) {
            var server = servers.get(i);
            button("Slot " + server.location().slot(), 460, 310 + (i%10)*50, 184, true,
                    () -> controller.selectServer(server.location()), server.location().equals(controller.selectedServer()));
        }
        button("<", 460, 835, 70, serverPage > 0, () -> serverPage--);
        button(">", 574, 835, 70, (serverPage+1)*10 < servers.size(), () -> serverPage++);
    }
    private void comparison() {
        panel(680, 260, 732, 365);
        textSize(17); label(controller.previousFrame() ? "PREVIOUS SUCCESSFUL FRAME / before failure" : "SERVER COMPARISON", 698, 290, ACCENT); textSize(15);
        controller.session().ifPresent(s -> label("Room: " + s.inventory().roomCode() + " | " + identity(controller.selectedServer()), 698, 320, INK));
        var result = controller.selectedResult();
        label("Source entity: " + result.map(r -> value(r.original().text("sourceEntityId"))).orElse("Pending"), 698, 347, MUTED);
        label("Original observation", 860, 385, MUTED); label("Runtime result", 1140, 385, ACCENT);
        label("Utilization", 698, 417, INK);
        label(result.map(r -> percent(r.original().number("utilizationFraction"))).orElse("Pending"), 860, 417, INK);
        label(result.map(r -> percent(r.resultUtilizationFraction())).orElse("Pending"), 1140, 417, INK);
        label("Power", 698, 447, INK);
        label(result.map(r -> watts(r.original().number("electricalPowerWatts"))).orElse("Pending"), 860, 447, INK);
        label(result.map(r -> watts(r.resultPowerWatts())).orElse("Pending"), 1140, 447, INK);
        label("Sample UTC: " + result.map(r -> value(r.original().text("timestampUtc"))).orElse("Pending"), 698, 482, MUTED);
        String age = result.map(r -> {
            String time = r.original().text("timestampUtc");
            return time == null ? "Not available" : Duration.between(Instant.parse(time), controller.lastFrame().orElseThrow().replayUtc()).toString();
        }).orElse("Pending");
        label("Sample age at query: " + age, 698, 510, MUTED);
        label("Quality: " + result.map(r -> value(r.original().text("quality"))).orElse("Pending")
                + " | Provenance: " + result.map(r -> value(r.original().text("provenance"))).orElse("Pending"), 698, 538, MUTED);
        var frame = controller.lastFrame();
        label("Replay UTC: " + frame.map(f -> f.replayUtc().toString()).orElse("Pending"), 698, 566, INK);
        label("Replay offset: " + frame.map(f -> f.replayOffset().toString()).orElse("Pending")
                + " | Engine elapsed: " + frame.map(f -> f.engineElapsed().toString()).orElse("Pending"), 698, 596, INK);
    }
    private void errors(float x, float y, float w, float h) {
        panel(x, y, w, h);
        label("REPLAY BLOCKED / diagnostics", x+14, y+26, 0xffffac93);
        List<String> wrapped = new ArrayList<>();
        for (String line : controller.errorLines()) {
            StringBuilder part = new StringBuilder();
            for (String word : line.split("\\s+")) {
                if (textWidth(part + word) > w-32 && !part.isEmpty()) { wrapped.add(part.toString()); part.setLength(0); }
                // Split long unbroken paths/identifiers as well.
                for (char c : (word + " ").toCharArray()) {
                    if (textWidth(part.toString() + c) > w-32) { wrapped.add(part.toString()); part.setLength(0); }
                    part.append(c);
                }
            }
            wrapped.add(part.toString());
        }
        int visible = (int)((h-70)/22);
        errorScroll = Math.max(0, Math.min(errorScroll, Math.max(0, wrapped.size()-visible)));
        button("Previous", x+w-196, y+7, 88, errorScroll > 0, () -> errorScroll -= visible);
        button("Next", x+w-100, y+7, 86, errorScroll+visible < wrapped.size(), () -> errorScroll += visible);
        for (int i=errorScroll; i<Math.min(wrapped.size(), errorScroll+visible); i++) label(wrapped.get(i), x+14, y+55+(i-errorScroll)*22, INK);
        label("Lines " + (errorScroll+1) + "–" + Math.min(wrapped.size(), errorScroll+visible) + " / " + wrapped.size(), x+14, y+h-12, MUTED);
    }
    @Override public void mouseWheel(MouseEvent event) { errorScroll += event.getCount()*3; }
    @Override public void mousePressed() {
        for (int i=0; i<bounds.size(); i++) {
            var b=bounds.get(i);
            if (mouseX>=b[0] && mouseX<=b[0]+b[2] && mouseY>=b[1] && mouseY<=b[1]+b[3]) { actions.get(i).run(); return; }
        }
    }
    private void panel(float x,float y,float w,float h) { noStroke(); fill(0xff192636); rect(x,y,w,h,10); }
    private void label(String value,float x,float y,int color) { fill(color); text(value,x,y); }
    private void button(String text,float x,float y,float w,boolean enabled,Runnable action) { button(text,x,y,w,enabled,action,false); }
    private void button(String text,float x,float y,float w,boolean enabled,Runnable action,boolean selected) {
        noStroke(); fill(selected ? 0xff226d66 : enabled ? 0xff2a3c51 : 0xff202c3a); rect(x,y,w,38,6);
        textSize(13); label(text,x+10,y+24,enabled ? INK : MUTED); textSize(15);
        if (enabled) { bounds.add(new float[]{x,y,w,38}); actions.add(action); }
    }
    private static String identity(ServerLocation location) {
        return location == null ? "Not available" : "Column " + location.column() + " | Rack " + location.rackCode().value() + " | Slot " + location.slot();
    }
    private static String value(Object value) { return value == null ? "Not available" : value.toString(); }
    private static String percent(Double value) { return value == null ? "Not available" : String.format(Locale.ROOT,"%.2f %%",value*100); }
    private static String watts(Double value) { return value == null ? "Not available" : String.format(Locale.ROOT,"%.2f W",value); }
}
