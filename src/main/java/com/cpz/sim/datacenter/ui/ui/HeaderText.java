package com.cpz.sim.datacenter.ui.ui;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;

/** Formats the effective data input mode shown in the header. */
public final class HeaderText {
    private HeaderText() { }

    public static String dataInputMode(DatacenterDefinition definition) {
        return "DATA INPUT MODE: " + switch (definition.dataInputMode()) {
            case UTILIZATION_DRIVEN -> "UTILIZATION DRIVEN";
            case POWER_DRIVEN -> "POWER DRIVEN - " + definition.powerInputGranularity();
            case TEMPERATURE_DRIVEN -> "TEMPERATURE DRIVEN - " + definition.temperatureInputGranularity();
        };
    }
}
