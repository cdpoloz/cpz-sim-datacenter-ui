package com.cpz.sim.datacenter.ui.ui;

import com.cpz.sim.datacenter.snapshot.DatacenterOperationalSnapshot;
import java.util.Locale;

/** Missing-data and unit formatting only; aggregate values come from backend snapshots. */
public final class MetricPresentation {
    private MetricPresentation() {}

    public static double onlineAverageTemperatureCelsius(DatacenterOperationalSnapshot snapshot) {
        return snapshot == null || !snapshot.hasOnlineServers()
                ? Double.NaN : snapshot.averageOnlineServerTemperatureCelsius();
    }

    public static String temperature(double value, String format) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, format, value) : "--";
    }

    /** Preserve visible standby consumption instead of rounding small powers to zero megawatts. */
    public static String power(double watts, String kilowattFormat, String megawattFormat) {
        if (!Double.isFinite(watts) || watts < 0) return "--";
        if (watts < 1_000.0) return String.format(Locale.ROOT, "%.0fW", watts);
        if (watts < 1_000_000.0) return String.format(Locale.ROOT, kilowattFormat, watts / 1_000.0);
        return String.format(Locale.ROOT, megawattFormat, watts / 1_000_000.0);
    }

    public static String power(double watts, String kilowattFormat) {
        return power(watts, kilowattFormat, "%.2fMW");
    }
}
