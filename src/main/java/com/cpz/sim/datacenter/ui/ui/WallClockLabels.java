package com.cpz.sim.datacenter.ui.ui;

import com.cpz.processing.controls.controls.label.Label;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Updates header labels from the system clock in the JVM's local time zone. */
public final class WallClockLabels {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private final Label date;
    private final Label time;
    private LocalDateTime displayed;

    public WallClockLabels(Label date, Label time) {
        this.date = date;
        this.time = time;
    }

    public void update() {
        update(LocalDateTime.now().withNano(0));
    }

    void update(LocalDateTime now) {
        if (now.equals(displayed)) return;
        time.setText(TIME.format(now));
        if (displayed == null || !now.toLocalDate().equals(displayed.toLocalDate())) {
            date.setText(DATE.format(now));
        }
        displayed = now;
    }
}
