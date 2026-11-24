package com.cpz.sim.datacenter.ui.ui.panel;

import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.controls.floatinglist.FloatingListConfigLoader;
import com.cpz.processing.controls.controls.floatinglist.FloatingListPanel;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.core.input.DefaultInputLayer;
import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.input.KeyboardEvent;
import com.cpz.processing.controls.core.input.PointerEvent;
import com.cpz.processing.controls.core.overlay.OverlayEntry;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.core.overlay.notification.NotificationManager;
import com.cpz.processing.controls.core.overlay.notification.NotificationSeverity;
import com.cpz.sim.datacenter.health.ServerAlertReason;
import com.cpz.sim.datacenter.health.ThermalAlertLevel;
import com.cpz.sim.datacenter.model.HardwareStatus;
import com.cpz.sim.datacenter.model.RackCode;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.snapshot.HealthSnapshot;
import com.cpz.sim.datacenter.snapshot.ServerHealthSnapshot;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.main.Sketch;
import com.cpz.sim.datacenter.ui.resources.ResourceContainer;
import com.cpz.sim.datacenter.ui.ui.SimulationAlertNotifier;
import com.cpz.sim.datacenter.ui.ui.render.StaticUiRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PImage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static com.cpz.processing.controls.core.input.PointerEvent.Type.*;
import static org.junit.jupiter.api.Assertions.*;

class AlertsPanelUpdaterTest {
    private final DrawingApplet sketch = new DrawingApplet();
    private final InputManager input = new InputManager();
    private final OverlayManager overlays = new OverlayManager();
    private final NotificationManager notifications = new NotificationManager(sketch, overlays);
    private final SimulationAlertNotifier notifier = new SimulationAlertNotifier(notifications);
    private final Label label = new Label(sketch, "lblAlerts", "STALE", 20, 20, 100, 20);
    private final Button trigger = new Button(sketch, "btnMoreAlerts", "", 260, 20, 70, 20);
    private final List<ServerAlertRowRenderer.Row> drawn = new ArrayList<>();
    private final FloatingListPanel<ServerLocation, ServerAlertRowRenderer.Row> panel =
            new FloatingListPanel<>(sketch, overlays, input, 1100,
                    new FloatingListPanel.Dimensions(120, 144, 20, 24, 10, 36),
                    "ACTIVE ALERTS", "Close", "No active alerts",
                    ServerAlertRowRenderer.Row::location, (row, bounds) -> drawn.add(row));
    private final AlertsPanelUpdater updater;

    AlertsPanelUpdaterTest() {
        trigger.setVisible(false);
        trigger.setEnabled(false);
        notifications.setMaxVisible(3);
        updater = new AlertsPanelUpdater(notifier, notifications, label, trigger, input, panel);
    }

    @AfterEach
    void dispose() {
        updater.close();
        notifications.dispose();
    }

    @Test
    void triggerUsesServerOverflowAndTracksTheConfiguredLimit() {
        var unrelated = notifications.showPersistent("Unrelated");
        update(1);
        assertTriggerHidden();
        assertEquals("ALERTS", label.getText());
        update(2, 1, 2, 3);
        assertTriggerHidden();
        assertEquals(4, notifications.size(), "Includes the unrelated item");
        click(260, 20);
        assertFalse(updater.isOpen());

        update(3, IntStream.rangeClosed(1, 8).toArray());
        assertTrue(trigger.isVisible());
        assertTrue(trigger.isEnabled());
        assertEquals("+5 more", trigger.getText());
        assertEquals("ALERTS", label.getText());
        assertEquals(9, notifications.size());

        update(4, IntStream.rangeClosed(2, 8).toArray());
        assertEquals("+4 more", trigger.getText());
        notifications.setMaxVisible(2);
        updater.update();
        assertEquals("+5 more", trigger.getText());
        notifications.setMaxVisible(7);
        updater.update();
        assertTriggerHidden();

        update(5);
        assertTriggerHidden();
        assertEquals("ALERTS", label.getText());
        assertEquals(1, notifications.size());
        assertTrue(notifications.dismiss(unrelated.getId()));
    }

    @Test
    void opensWithAllServerAlertsAndAppliesLiveUpdatesThroughEmptyState() {
        update(1, 4, 2, 3, 1);
        click(260, 20);
        updater.update();
        assertTrue(updater.isOpen());
        assertEquals("ALERTS", label.getText());
        assertEquals("ACTIVE ALERTS · 4", panel.getTitle());
        assertEquals(List.of("S01", "S02", "S03", "S04"), renderSlots());
        assertEquals(new ServerAlertRowRenderer.Row(new ServerLocation("C01", "R01", "S01"),
                "C01-R01-S01", NotificationSeverity.WARNING, "High temperature", "00d 00:01"), drawn.getFirst());
        var initial = notifier.activeAlerts();
        update(2, 1, 2, 3, 4);
        assertSame(initial, notifier.activeAlerts(), "An unchanged later tick should reuse the published list");

        update(3, 3, 2, 5);
        assertTrue(updater.isOpen());
        assertTriggerHidden();
        assertEquals("ALERTS", label.getText());
        assertEquals("ACTIVE ALERTS · 3", panel.getTitle());
        assertEquals(List.of("S02", "S03", "S05"), renderSlots());
        assertEquals("00d 00:01", drawn.getFirst().observedTime());
        assertEquals("00d 00:03", drawn.getLast().observedTime());

        update(4);
        assertTrue(updater.isOpen());
        assertTriggerHidden();
        assertEquals("ALERTS", label.getText());
        assertEquals("ACTIVE ALERTS · 0", panel.getTitle());
        assertEquals(List.of(), renderSlots());
        assertTrue(sketch.drawnText.contains("No active alerts"));
        assertEquals(0, notifications.size());
    }

    @Test
    void liveTitleUpdateKeepsOpenOverlayAndVisibleLocation() {
        update(1, IntStream.rangeClosed(1, 10).toArray());
        click(260, 20);
        var overlay = overlays.getTopOverlay().orElseThrow();
        input.dispatchPointer(new PointerEvent(WHEEL, 40, 80, PApplet.LEFT, 2, false, false));
        assertEquals("S03", renderSlots().getFirst());

        update(2, IntStream.rangeClosed(1, 11).toArray());
        assertEquals("ACTIVE ALERTS · 11", panel.getTitle());
        assertSame(overlay, overlays.getTopOverlay().orElseThrow());
        assertEquals("S03", renderSlots().getFirst());
    }

    @Test
    void presentationKeepsRelativeMeasuresAndUsesDashboardColors() {
        String path = "data/config/floating-list-alerts.json";
        var presentation = PApplet.loadJSONObject(new File(path));
        FloatingListConfigLoader.applyFromJson(presentation, path, panel);
        var measures = panel.getMeasures();
        assertEquals(556, Math.round(measures.width().resolve(1014)));
        assertEquals(395, Math.round(measures.width().resolve(720)));
        assertEquals(22, Math.round(measures.canvasMargin().resolve(1014)));
        assertEquals(0xFF00121B, panel.getStyle().frame().backgroundColor());
        assertEquals(0xFF14323B, panel.getStyle().closeButton().backgroundColor());

        var rows = ServerAlertRowRenderer.Style.load(presentation.getJSONObject("rowRenderer"), path);
        assertEquals(14, Math.round(rows.textSize().resolve(1014)));
        assertEquals(10, Math.round(rows.textSize().resolve(720)));
    }

    @Test
    void allClosePathsPreserveNotificationsAndTheOutsideGestureStopsAtThePanel() {
        List<PointerEvent.Type> lowerEvents = new ArrayList<>();
        input.registerLayer(new DefaultInputLayer(0) {
            @Override public boolean handlePointerEvent(PointerEvent event) {
                lowerEvents.add(event.getType());
                return true;
            }
        });

        update(1, 1, 2, 3, 4);
        var alerts = notifier.activeAlerts();

        click(260, 20);
        assertTrue(updater.isOpen());
        click(122, 52);
        assertClosedWith(alerts);

        click(260, 20);
        assertTrue(updater.isOpen());
        input.dispatchKeyboard(new KeyboardEvent(KeyboardEvent.Type.PRESS, PApplet.ESC, PApplet.ESC,
                false, false, false));
        assertClosedWith(alerts);

        click(260, 20);
        assertTrue(updater.isOpen());
        input.dispatchPointer(new PointerEvent(PRESS, 280, 200));
        input.dispatchPointer(new PointerEvent(DRAG, 250, 205));
        input.dispatchPointer(new PointerEvent(RELEASE, 250, 205));
        assertClosedWith(alerts);
        assertTrue(lowerEvents.isEmpty());

        click(280, 200);
        assertEquals(List.of(PRESS, RELEASE), lowerEvents);

        click(260, 20);
        assertTrue(updater.isOpen());
        updater.close();
        assertClosedWith(alerts);
        click(260, 20);
        assertFalse(updater.isOpen());
        assertEquals(List.of(PRESS, RELEASE, PRESS, RELEASE), lowerEvents);
    }

    @Test
    void classicOverlayPassPaintsHigherZAfterLowerZ() {
        List<Integer> painted = new ArrayList<>();
        for (int priority : List.of(1100, 10, 100, 50)) {
            overlays.register(new OverlayEntry(priority, () -> painted.add(priority), new DefaultInputLayer(priority)));
        }
        var resources = new ResourceContainer();
        resources.setStaticOverlay(new PImage(1, 1));
        new StaticUiRenderer(new ApplicationContext(sketch), resources, overlays).drawOverlay();
        assertEquals(List.of(10, 50, 100, 1100), painted);
    }

    private void assertClosedWith(List<?> alerts) {
        assertFalse(updater.isOpen());
        updater.update();
        assertEquals("ALERTS", label.getText());
        assertSame(alerts, notifier.activeAlerts());
        assertEquals(4, notifications.size());
    }

    private void assertTriggerHidden() {
        assertFalse(trigger.isVisible());
        assertFalse(trigger.isEnabled());
    }

    private void update(long tick, int... slots) {
        notifier.update(new HealthSnapshot(tick, tick * 60,
                IntStream.of(slots).mapToObj(AlertsPanelUpdaterTest::warning).toList()));
        updater.update();
    }

    private void click(int x, int y) {
        input.dispatchPointer(new PointerEvent(PRESS, x, y));
        input.dispatchPointer(new PointerEvent(RELEASE, x, y));
    }

    private List<String> renderSlots() {
        drawn.clear();
        sketch.drawnText.clear();
        overlays.getTopOverlay().orElseThrow().getRender().run();
        return drawn.stream().map(row -> row.location().slot()).toList();
    }

    private static ServerHealthSnapshot warning(int number) {
        String slot = "S%02d".formatted(number);
        return new ServerHealthSnapshot("C01-R01-" + slot, "C01", new RackCode("R01"), slot,
                HardwareStatus.ALERT, Set.of(ServerAlertReason.HIGH_TEMPERATURE), .5, 85, ThermalAlertLevel.ELEVATED);
    }

    private static final class DrawingApplet extends Sketch {
        private final List<String> drawnText = new ArrayList<>();

        private DrawingApplet() {
            width = 300;
            height = 260;
            g = new PGraphics();
            g.setParent(this);
        }

        @Override public void pushMatrix() {}
        @Override public void popMatrix() {}
        @Override public void pushStyle() {}
        @Override public void popStyle() {}
        @Override public void translate(float x, float y) {}
        @Override public void image(PImage image, float x, float y, float width, float height) {}
        @Override public void textSize(float size) {}
        @Override public void text(String text, float x, float y) {}
        @Override public void text(String text, float x, float y, float width, float height) { drawnText.add(text); }
        @Override public void clip(float x, float y, float width, float height) {}
        @Override public void noClip() {}
    }
}
