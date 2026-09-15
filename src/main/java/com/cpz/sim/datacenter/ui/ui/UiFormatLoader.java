package com.cpz.sim.datacenter.ui.ui;

import static com.cpz.sim.datacenter.ui.main.Launcher.PROPS;

/**
 * Loads UI number formats.
 *
 * @author CPZ
 */
public class UiFormatLoader {

    public UiFormatLoader() {
    }

    public void loadInto(UiFormatContainer container) {
        container.setPercentage(PROPS.getProperty("number.format.percentage"));
        container.setTemperature(PROPS.getProperty("number.format.temperature"));
        container.setSimpleTemperature(PROPS.getProperty("number.format.temperature.simple"));
        container.setPowerKw(PROPS.getProperty("number.format.power.kw"));
        container.setPowerMw(PROPS.getProperty("number.format.power.mw"));
        container.setSpeed(PROPS.getProperty("number.format.speed"));
        container.setPressure(PROPS.getProperty("number.format.pressure"));
        container.setAirflow(PROPS.getProperty("number.format.airflow"));
    }
}