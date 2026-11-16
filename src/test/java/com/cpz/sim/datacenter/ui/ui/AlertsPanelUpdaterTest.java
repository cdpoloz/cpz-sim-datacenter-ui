package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.core.overlay.notification.NotificationManager;
import com.cpz.sim.datacenter.ui.ui.panel.AlertsPanelUpdater;
import org.junit.jupiter.api.Test;
import processing.core.PApplet;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class AlertsPanelUpdaterTest {
    @Test
    void titleFollowsPendingRetentionDismissalAndConfiguredVisibleLimit() {
        var sketch = new PApplet();
        var notifications = new NotificationManager(sketch, new OverlayManager());
        notifications.setMaxVisible(3);
        var label = new Label(sketch, "lblAlerts", "STALE", 0, 0, 300, 20);
        var updater = new AlertsPanelUpdater(notifications, label);
        updater.update();
        assertEquals("ALERTS", label.getText());
        var ids = new ArrayList<String>();
        for (int i = 0; i < 8; i++) ids.add(notifications.showPersistent("Alert " + i).getId());
        updater.update();
        assertEquals("ALERTS - 3 visible/5 more actives", label.getText());
        assertTrue(notifications.dismiss(ids.getFirst()));
        updater.update();
        assertEquals("ALERTS - 3 visible/4 more actives", label.getText());
        notifications.setMaxVisible(2);
        updater.update();
        assertEquals("ALERTS - 2 visible/5 more actives", label.getText());
        notifications.clear();
        updater.update();
        assertEquals("ALERTS", label.getText());
    }

    @Test
    void titleReportsActualVisibleCountWhenThePanelIsNotFull() {
        var sketch = new PApplet();
        var notifications = new NotificationManager(sketch, new OverlayManager());
        notifications.setMaxVisible(3);
        var label = new Label(sketch, "lblAlerts", "", 0, 0, 300, 20);
        var updater = new AlertsPanelUpdater(notifications, label);
        for (int i = 1; i <= 3; i++) {
            notifications.showPersistent("Alert " + i);
            updater.update();
            assertEquals("ALERTS - " + i + " visible/0 more actives", label.getText());
        }
    }
}
