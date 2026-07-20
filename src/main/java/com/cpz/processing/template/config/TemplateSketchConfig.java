package com.cpz.processing.template.config;

import com.cpz.processing.controls.controls.Control;
import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.controls.checkbox.Checkbox;
import com.cpz.processing.controls.controls.config.ControlConfigLoader;
import com.cpz.processing.controls.controls.dropdown.DropDown;
import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.numericfield.NumericField;
import com.cpz.processing.controls.controls.progressbar.ProgressBar;
import com.cpz.processing.controls.controls.radiogroup.RadioGroup;
import com.cpz.processing.controls.controls.slider.Slider;
import com.cpz.processing.controls.controls.textfield.TextField;
import com.cpz.processing.controls.controls.toggle.Toggle;
import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.core.overlay.tooltip.TooltipArea;
import com.cpz.processing.controls.core.overlay.tooltip.TooltipFactory;
import com.cpz.processing.controls.core.overlay.tooltip.util.TooltipOverlayController;
import com.cpz.processing.template.main.TemplateSketch;
import processing.opengl.PJOGL;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.cpz.processing.template.main.Launcher.LOG;
import static com.cpz.processing.template.main.Launcher.PROPS;
import static processing.core.PConstants.P2D;

/**
 * @author CPZ
 */
public class TemplateSketchConfig {

    public static void settings(TemplateSketch sk) {
        if (sk == null) return;
        LOG.info("Starting settings");
        PJOGL.setIcon("data" + File.separator + "img" + File.separator + PROPS.getProperty("window.icon"));
        // tamaño de ventana
        sk.size(Integer.parseInt(PROPS.getProperty("sketch.width")), Integer.parseInt(PROPS.getProperty("sketch.height")), P2D);
        // smoothing
        sk.smooth(Integer.parseInt(PROPS.getProperty("sketch.smoothing")));
        LOG.info("Finished settings");
    }

    public static void initialSetup(TemplateSketch sk) {
        if (sk == null) return;
        LOG.info("Starting initial setup");
        sk.frameRate(Integer.parseInt(PROPS.getProperty("sketch.fps")));
        sk.getSurface().setTitle(PROPS.getProperty("window.title"));
        LOG.info("Finished initial setup");
    }

    public static Map<String, Control> setupControls(TemplateSketch sk, OverlayManager overlayManager, InputManager inputManager) {
        if (sk == null || overlayManager == null || inputManager == null) return null;
        String templateConfigPath = "data" + File.separator + "config" + File.separator + "template-sketch.json";
        ControlConfigLoader loader = new ControlConfigLoader(sk, overlayManager, inputManager);
        Map<String, Control> controles = loader.load(templateConfigPath);
        controles
                .values()
                .stream()
                .filter(c -> c instanceof Button)
                .forEach(btn -> ((Button) btn).setClickListener(() -> sk.btnClicked(btn.getCode())));
        controles
                .values()
                .stream()
                .filter(c -> c instanceof Checkbox)
                .forEach(chk -> ((Checkbox) chk).setChangeListener(estado -> sk.chkClicked(chk.getCode(), estado)));
        controles
                .values()
                .stream()
                .filter(c -> c instanceof DropDown)
                .forEach(dd -> ((DropDown) dd).setChangeListener(estado -> sk.ddChanged((DropDown) dd)));
        controles
                .values()
                .stream()
                .filter(c -> c instanceof NumericField)
                .forEach(nf -> ((NumericField) nf).setChangeListener(text -> sk.nfChanged((NumericField) nf)));
        controles
                .values()
                .stream()
                .filter(c -> c instanceof RadioGroup)
                .forEach(rg -> ((RadioGroup) rg).setChangeListener(selectedOption -> sk.rgClicked((RadioGroup) rg)));
        controles
                .values()
                .stream()
                .filter(c -> c instanceof Slider)
                .forEach(sld -> ((Slider) sld).setChangeListener(valor -> sk.sldChanged(sld.getCode(), valor)));
        controles
                .values()
                .stream()
                .filter(c -> c instanceof TextField)
                .forEach(tf -> ((TextField) tf).setChangeListener(text -> sk.tfChanged(tf.getCode(), text)));
        controles
                .values()
                .stream()
                .filter(c -> c instanceof Toggle)
                .forEach(tgl -> ((Toggle) tgl).setChangeListener(estado -> sk.tglClicked(tgl.getCode(), estado)));
        return controles;
    }

    public static Map<String, Button> filterButtons(Map<String, Control> controles) {
        if (controles == null) return null;
        Map<String, Button> botones = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Button).forEach(btn -> botones.put(btn.getCode(), (Button) btn));
        return botones;
    }

    public static Map<String, Checkbox> filterCheckboxes(Map<String, Control> controles) {
        if (controles == null) return null;
        Map<String, Checkbox> checkboxes = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Checkbox).forEach(chk -> checkboxes.put(chk.getCode(), (Checkbox) chk));
        return checkboxes;
    }

    public static Map<String, DropDown> filterDropdowns(Map<String, Control> controles) {
        if (controles == null) return null;
        Map<String, DropDown> dropdowns = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof DropDown).forEach(dd -> dropdowns.put(dd.getCode(), (DropDown) dd));
        return dropdowns;
    }

    public static Map<String, Indicator> filterIndicators(Map<String, Control> controles) {
        if (controles == null) return null;
        Map<String, Indicator> indicators = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicators.put(ind.getCode(), (Indicator) ind));
        return indicators;
    }

    public static Map<String, Label> filterLabels(Map<String, Control> controles) {
        if (controles == null) return null;
        Map<String, Label> labels = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Label).forEach(lbl -> labels.put(lbl.getCode(), (Label) lbl));
        return labels;
    }

    public static Map<String, NumericField> filterNumericfields(Map<String, Control> controles) {
        if (controles == null) return null;
        Map<String, NumericField> numericfields = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof NumericField).forEach(nf -> numericfields.put(nf.getCode(), (NumericField) nf));
        return numericfields;
    }

    public static Map<String, ProgressBar> filterProgressbars(Map<String, Control> controles) {
        if (controles == null) return null;
        Map<String, ProgressBar> progressbars = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof ProgressBar).forEach(pb -> progressbars.put(pb.getCode(), (ProgressBar) pb));
        return progressbars;
    }

    public static Map<String, RadioGroup> filterRadiogroups(Map<String, Control> controles) {
        if (controles == null) return null;
        Map<String, RadioGroup> radiogroups = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof RadioGroup).forEach(rg -> radiogroups.put(rg.getCode(), (RadioGroup) rg));
        return radiogroups;
    }

    public static Map<String, Slider> filterSliders(Map<String, Control> controles) {
        if (controles == null) return null;
        Map<String, Slider> sliders = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Slider).forEach(sld -> sliders.put(sld.getCode(), (Slider) sld));
        return sliders;
    }

    public static Map<String, TextField> filterTextfields(Map<String, Control> controles) {
        if (controles == null) return null;
        Map<String, TextField> textfields = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof TextField).forEach(tf -> textfields.put(tf.getCode(), (TextField) tf));
        return textfields;
    }

    public static Map<String, Toggle> filterToggles(Map<String, Control> controles) {
        if (controles == null) return null;
        Map<String, Toggle> toggles = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Toggle).forEach(tgl -> toggles.put(tgl.getCode(), (Toggle) tgl));
        return toggles;
    }

    public static TooltipOverlayController setupTooltips(TemplateSketch sk, OverlayManager overlayManager, Map<String, Control> controls) {
        if (sk == null) return null;
        TooltipOverlayController tooltips = new TooltipOverlayController(sk, overlayManager);
        // custom area tooltips
        TooltipArea customTooltipArea = new TooltipArea(750, 350.0f, 200.0f, 100.0f)
                .setTooltip(TooltipFactory.loadFromJson(sk, "data/config/custom-area-tooltip.json"));
        tooltips.registerTarget(customTooltipArea);
        // tooltips over Control objects (examples)
        tooltips.registerTarget((Button) controls.get("btnTemplate"));
        tooltips.registerTarget((Slider) controls.get("sldTemplate"));
        tooltips.registerTarget((Indicator) controls.get("indTemplate"));
        tooltips.registerTarget((ProgressBar) controls.get("pbTemplate"));
        return tooltips;
    }
}
