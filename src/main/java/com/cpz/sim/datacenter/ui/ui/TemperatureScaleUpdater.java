package com.cpz.sim.datacenter.ui.ui;

import com.cpz.sim.datacenter.snapshot.TemperatureSnapshot;

/** Expands the shared color scale and its legend to contain observed temperatures. */
public final class TemperatureScaleUpdater {
    private final UiStateContainer state;
    private final UiComponentContainer controls;
    private final String format;
    private TemperatureSnapshot previousSnapshot;

    public TemperatureScaleUpdater(UiStateContainer state, UiComponentContainer controls, String format) {
        this.state = state;
        this.controls = controls;
        this.format = format;
    }

    public void update(TemperatureSnapshot snapshot) {
        if (snapshot == null || snapshot == previousSnapshot) return;
        previousSnapshot = snapshot;
        float minimum = state.minServerTemperatureCelsius();
        float maximum = state.maxServerTemperatureCelsius();
        for (var server : snapshot.servers()) {
            double temperature = server.temperatureCelsius();
            if (temperature < minimum) minimum = (float) (Math.floor(temperature / 10.0) * 10.0);
            if (temperature > maximum) maximum = (float) (Math.ceil(temperature / 10.0) * 10.0);
        }
        // An empty or degenerate initial range must still support color interpolation.
        if (maximum <= minimum) maximum = minimum + 10.0f;
        if (minimum != state.minServerTemperatureCelsius() || maximum != state.maxServerTemperatureCelsius()) {
            state.setMinServerTemperatureCelsius(minimum);
            state.setMaxServerTemperatureCelsius(maximum);
            state.setUpdateUI(true);
        }
        // Legend and all panel colors use exactly the same bounds in this frame.
        for (int i = 0; i <= 5; i++) {
            float temperature = minimum + (maximum - minimum) * i / 5.0f;
            controls.labels().get("lblRoomTemperatureScale0" + (i + 1))
                    .setText(String.format(java.util.Locale.ROOT, format, temperature));
        }
    }
}
