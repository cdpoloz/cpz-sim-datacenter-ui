package com.cpz.sim.datacenter.ui.ui;

/**
 * Holds {@link String#format(String, Object...)} patterns used by panel updaters.
 *
 * @author CPZ
 */
public class UiFormatContainer {

    private String temperature;
    private String simpleTemperature;
    private String percentage;
    private String powerKw;
    private String powerMw;
    private String speed;
    private String pressure;
    private String airflow;

    /** Creates an empty format container populated by {@link UiFormatLoader}. */
    public UiFormatContainer() {
    }

    public String temperature() {
        return temperature;
    }

    public void setTemperature(String temperature) {
        this.temperature = temperature;
    }

    public String simpleTemperature() {
        return simpleTemperature;
    }

    public void setSimpleTemperature(String simpleTemperature) {
        this.simpleTemperature = simpleTemperature;
    }

    public String percentage() {
        return percentage;
    }

    public void setPercentage(String percentage) {
        this.percentage = percentage;
    }

    public String powerKw() {
        return powerKw;
    }

    public void setPowerKw(String powerKw) {
        this.powerKw = powerKw;
    }

    public String powerMw() {
        return powerMw;
    }

    public void setPowerMw(String powerMw) {
        this.powerMw = powerMw;
    }

    public String speed() {
        return speed;
    }

    public void setSpeed(String speed) {
        this.speed = speed;
    }

    public String pressure() {
        return pressure;
    }

    public void setPressure(String pressure) {
        this.pressure = pressure;
    }

    public String airflow() {
        return airflow;
    }

    public void setAirflow(String airflow) {
        this.airflow = airflow;
    }
}
