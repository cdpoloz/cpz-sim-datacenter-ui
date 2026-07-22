package com.cpz.processing.template.main;

import com.cpz.processing.controls.controls.Control;
import com.cpz.processing.controls.controls.config.ControlConfigLoader;
import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.input.ProcessingKeyboardAdapter;
import processing.core.PApplet;
import processing.core.PImage;
import processing.opengl.PJOGL;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.cpz.processing.template.main.Launcher.LOG;
import static com.cpz.processing.template.main.Launcher.PROPS;
import static com.cpz.processing.template.util.Constantes.COLOR_FONDO;

/**
 * @author CPZ
 */
public class Sketch extends PApplet {

    private InputManager inputManager;
    private OverlayManager overlayManager;
    private ProcessingKeyboardAdapter processingKeyboardAdapter;
    private Map<String, Control> controls;
    private Map<String, Indicator> indicators;
    private Map<String, Label> labels;

    private PImage fondo, overlayEstatico;
    private boolean showOverlayEstatico;

    public void settings() {
        LOG.info("Starting settings");
        PJOGL.setIcon("data" + File.separator + "img" + File.separator + PROPS.getProperty("window.icon"));
        // tamaño de ventana
        size(Integer.parseInt(PROPS.getProperty("sketch.width")), Integer.parseInt(PROPS.getProperty("sketch.height")), P2D);
        // smoothing
        smooth(Integer.parseInt(PROPS.getProperty("sketch.smoothing")));
        LOG.info("Finished settings");
    }

    public void setup() {
        LOG.info("Starting initial setup");
        background(COLOR_FONDO);
        frameRate(Integer.parseInt(PROPS.getProperty("sketch.fps")));
        getSurface().setTitle(PROPS.getProperty("window.title"));
        LOG.info("Finished initial setup");
        // input manager
        inputManager = new InputManager();
        // overlay manager
        overlayManager = new OverlayManager();
        // labels
        String labelsConfigPath = "data" + File.separator + "config" + File.separator + "labels.json";
        ControlConfigLoader loader = new ControlConfigLoader(this, overlayManager, inputManager);
        Map<String, Control> controles = loader.load(labelsConfigPath);
        labels = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Label).forEach(lbl -> labels.put(lbl.getCode(), (Label) lbl));
        // font
        textFont(createFont("data"+ File.separator + "font" + File.separator + "JetBrainsMono.ttf", 56, true));
        // imágenes
        fondo = loadImage("data" + File.separator + "img" + File.separator + "ui_fondo.png");
        overlayEstatico = loadImage("data" + File.separator + "img" + File.separator + "ui_overlay.png");
        // debug
        showOverlayEstatico = true;
    }

    public void draw() {
        dibujarFondo();
        labels.values().forEach(Label::draw);
        if (showOverlayEstatico) dibujarOverlayEstatico();
    }

    private void dibujarFondo() {
        pushStyle();
        imageMode(CORNER);
        image(fondo, 0, 0, width, height);
        popStyle();
    }

    private void dibujarOverlayEstatico() {
        pushStyle();
        imageMode(CORNER);
        image(overlayEstatico, 0, 0, width, height);
        popStyle();
    }

    @Override
    public void keyReleased() {
        if (key == 'e') showOverlayEstatico = !showOverlayEstatico;
    }

}
