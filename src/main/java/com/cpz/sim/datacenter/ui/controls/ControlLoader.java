package com.cpz.sim.datacenter.ui.controls;

import com.cpz.processing.controls.controls.Control;
import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.controls.config.ControlConfigLoader;
import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.toggle.Toggle;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads UI controls from JSON configuration files.
 *
 * <p>This class is responsible only for constructing control collections.
 * It does not register listeners, input layers or overlays.</p>
 *
 * @author CPZ
 */
public class ControlLoader extends ApplicationComponent {

    /**
     * Creates a loader that supplies the active Processing sketch to control configuration.
     *
     * @param context application context containing the sketch
     */
    public ControlLoader(ApplicationContext context) {
        super(context);
    }

    /**
     * Loads only Labels from a control configuration file.
     *
     * @param loader configured controls-library loader
     * @param path JSON file path
     * @return Labels indexed by stable control code
     */
    public Map<String, Label> loadLabels(ControlConfigLoader loader, String path) {
        Map<String, Control> controls = loader.load(path);
        Map<String, Label> result = new HashMap<>();
        controls.values()
                .stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .forEach(label -> result.put(label.getCode(), label));
        return result;
    }

    /**
     * Loads only Indicators from a control configuration file.
     *
     * @param loader configured controls-library loader
     * @param path JSON file path
     * @return Indicators indexed by stable control code
     */
    public Map<String, Indicator> loadIndicators(ControlConfigLoader loader, String path) {
        Map<String, Control> controls = loader.load(path);
        Map<String, Indicator> result = new HashMap<>();
        controls.values()
                .stream()
                .filter(Indicator.class::isInstance)
                .map(Indicator.class::cast)
                .forEach(indicator -> result.put(indicator.getCode(), indicator));
        return result;

    }

    /**
     * Loads only Buttons from a control configuration file.
     *
     * @param loader configured controls-library loader
     * @param path JSON file path
     * @return Buttons indexed by stable control code
     */
    public Map<String, Button> loadButtons(ControlConfigLoader loader, String path) {
        Map<String, Control> controls = loader.load(path);
        Map<String, Button> result = new HashMap<>();
        controls.values()
                .stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .forEach(button -> result.put(button.getCode(), button));
        return result;

    }

    /**
     * Loads only Toggles from a control configuration file.
     *
     * @param loader configured controls-library loader
     * @param path JSON file path
     * @return Toggles indexed by stable control code
     */
    public Map<String, Toggle> loadToggles(ControlConfigLoader loader, String path) {
        Map<String, Control> controls = loader.load(path);
        Map<String, Toggle> result = new HashMap<>();
        controls.values()
                .stream()
                .filter(Toggle.class::isInstance)
                .map(Toggle.class::cast)
                .forEach(toggle -> result.put(toggle.getCode(), toggle));
        return result;
    }

}
