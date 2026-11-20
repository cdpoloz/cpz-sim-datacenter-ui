package com.cpz.sim.datacenter.ui.ui.panel;

import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.controls.config.ControlConfigLoader;
import com.cpz.processing.controls.controls.floatinglist.FloatingListConfigLoader;
import com.cpz.processing.controls.controls.floatinglist.FloatingListPanel;
import com.cpz.processing.controls.controls.geometry.ResolvedBounds;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.core.input.DefaultInputLayer;
import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.input.PointerEvent;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.core.overlay.notification.NotificationManager;
import com.cpz.processing.controls.core.util.JsonConfigSupport;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.ui.ui.ServerAlertPresentation;
import com.cpz.sim.datacenter.ui.ui.SimulationAlertNotifier;
import processing.core.PApplet;

import java.util.List;

/** Connects the classic alert heading and trigger to the library's floating list. */
public final class AlertsPanelUpdater implements AutoCloseable {
    private static final int LIST_PRIORITY = 1100;
    private static final int TRIGGER_PRIORITY = 1;
    private static final String PRESENTATION_PATH = "data/config/floating-list-alerts.json";

    private final SimulationAlertNotifier serverAlerts;
    private final NotificationManager notifications;
    private final Label title;
    private final Button trigger;
    private final InputManager input;
    private final DefaultInputLayer triggerLayer;
    private final FloatingListPanel<ServerLocation, ServerAlertRowRenderer.Row> panel;
    private final String panelTitle;
    private List<ServerAlertPresentation> displayed = List.of();
    private int displayedTotal = -1;

    public static AlertsPanelUpdater create(PApplet sketch, OverlayManager overlays, InputManager input,
                                            SimulationAlertNotifier serverAlerts, NotificationManager notifications,
                                            Label title) {
        var config = new ControlConfigLoader(sketch, overlays, input);
        var trigger = (Button) config.load("data/config/buttonsAlerts.json").get("btnMoreAlerts");
        var presentation = JsonConfigSupport.loadRequiredObject(sketch, PRESENTATION_PATH, "active alerts");
        // The loader patches an existing instance. The JSON supplies the live canvas-relative measures.
        var panel = new FloatingListPanel<>(sketch, overlays, input, LIST_PRIORITY,
                new FloatingListPanel.Dimensions(390, 286, 34, 42, 16, 82),
                "ACTIVE ALERTS", "Close", "No active alerts",
                ServerAlertRowRenderer.Row::location,
                new ServerAlertRowRenderer(sketch, notifications.getStyle(), presentation, PRESENTATION_PATH));
        FloatingListConfigLoader.applyFromJson(presentation, PRESENTATION_PATH, panel);
        return new AlertsPanelUpdater(serverAlerts, notifications, title, trigger, input, panel);
    }

    AlertsPanelUpdater(SimulationAlertNotifier serverAlerts, NotificationManager notifications, Label title,
                       Button trigger, InputManager input,
                       FloatingListPanel<ServerLocation, ServerAlertRowRenderer.Row> panel) {
        this.serverAlerts = serverAlerts;
        this.notifications = notifications;
        this.title = title;
        this.trigger = trigger;
        this.input = input;
        this.panel = panel;
        panelTitle = panel.getTitle();
        title.setText("ALERTS");
        trigger.setClickListener(() -> panel.open(this::headingBounds));
        triggerLayer = new DefaultInputLayer(TRIGGER_PRIORITY) {
            @Override
            public boolean handlePointerEvent(PointerEvent event) {
                boolean consumed = trigger.canConsumePointerEvent(event);
                trigger.handlePointerEvent(event);
                return consumed;
            }
        };
        input.registerLayer(triggerLayer);
    }

    public void update() {
        var alerts = serverAlerts.activeAlerts();
        if (alerts != displayed) {
            panel.setItems(alerts.stream().map(ServerAlertRowRenderer.Row::from).toList());
            displayed = alerts;
        }

        int total = alerts.size();
        if (total != displayedTotal) {
            panel.setTitle(panelTitle + " · " + total);
            displayedTotal = total;
        }

        int overflow = total - notifications.getMaxVisible();
        boolean canOpen = overflow > 0;
        if (canOpen != trigger.isVisible()) {
            trigger.setEnabled(canOpen);
            trigger.setVisible(canOpen);
        }
        if (canOpen) {
            String text = "+" + overflow + " more";
            if (!text.equals(trigger.getText())) trigger.setText(text);
        }
    }

    public boolean isOpen() {
        return panel.isOpen();
    }

    public void drawTrigger() {
        trigger.draw();
    }

    @Override
    public void close() {
        panel.close();
        input.unregisterLayer(triggerLayer);
    }

    private ResolvedBounds headingBounds() {
        var bounds = title.getTooltipBounds();
        return new ResolvedBounds(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }
}
