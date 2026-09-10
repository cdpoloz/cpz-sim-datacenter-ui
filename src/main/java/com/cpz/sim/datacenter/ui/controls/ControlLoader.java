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

    public ControlLoader(ApplicationContext context) {
        super(context);
    }

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
