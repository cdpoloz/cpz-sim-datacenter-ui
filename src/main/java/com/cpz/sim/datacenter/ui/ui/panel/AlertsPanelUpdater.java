package com.cpz.sim.datacenter.ui.ui.panel;

import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.core.overlay.notification.NotificationManager;
import java.util.Objects;

/** Keeps the panel title aligned with visible and pending notifications, including during pause. */
public final class AlertsPanelUpdater {
    private final NotificationManager notifications;
    private final Label title;

    public AlertsPanelUpdater(NotificationManager notifications, Label title) {
        this.notifications = Objects.requireNonNull(notifications);
        this.title = Objects.requireNonNull(title);
    }

    public void update() {
        int total = notifications.size();
        if (total == 0) {
            title.setText("ALERTS");
            return;
        }
        int visible = Math.min(total, notifications.getMaxVisible());
        title.setText("ALERTS - " + visible + " visible/" + (total - visible) + " more actives");
    }
}
