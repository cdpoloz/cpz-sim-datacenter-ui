package com.cpz.sim.datacenter.ui.ui.render;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TemperatureGraphRangeTest {
    @Test void includesTemperaturesAboveTheOldLimitWithHeadroom() {
        var range = TemperatureGraphRange.from(List.of(28.6, 51.2, 75.3, 77.1), 74);
        assertEquals(0, range.minimum());
        assertEquals(80, range.maximum());
        assertEquals(20, range.tick(1));
        assertEquals(range.maximum(), range.tick(4));
        assertTrue(range.maximum() > 77.1);
    }

    @Test void expandsForHotterOrNegativeAirAndIgnoresMissingValues() {
        var range = TemperatureGraphRange.from(List.of(Double.NaN, -25.0, 106.0), 74);
        assertEquals(-40, range.minimum());
        assertEquals(120, range.maximum());
        assertEquals(0, range.tick(1));
    }

    @Test void resetsToBaselineWhenHistoryIsCleared() {
        assertEquals(new TemperatureGraphRange(0, 80), TemperatureGraphRange.from(List.of(), 74));
    }
}
