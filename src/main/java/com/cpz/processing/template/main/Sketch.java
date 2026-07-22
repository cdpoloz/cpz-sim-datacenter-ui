package com.cpz.processing.template.main;

import com.cpz.processing.controls.controls.Control;
import com.cpz.processing.controls.controls.config.ControlConfigLoader;
import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.core.input.InputManager;
import com.cpz.processing.controls.core.overlay.OverlayManager;
import com.cpz.processing.controls.input.ProcessingKeyboardAdapter;
import com.cpz.utils.time.Timer;
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

    private Timer timerSimulacion;

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
        // controles
        Map<String, Control> controles;
        // labels
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "label.json");
        labels = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Label).forEach(lbl -> labels.put(lbl.getCode(), (Label) lbl));
        // indicators
        controles = new ControlConfigLoader(this).load("data" + File.separator + "config" + File.separator + "indicator.json");
        indicators = new HashMap<>();
        controles.values().stream().filter(c -> c instanceof Indicator).forEach(ind -> indicators.put(ind.getCode(), (Indicator) ind));
        // font
        textFont(createFont("data" + File.separator + "font" + File.separator + "JetBrainsMono.ttf", 56, true));
        // imágenes
        fondo = loadImage("data" + File.separator + "img" + File.separator + "ui_fondo.png");
        overlayEstatico = loadImage("data" + File.separator + "img" + File.separator + "ui_overlay.png");
        // timers
        timerSimulacion = new Timer();
        timerSimulacion.setPeriodMillis(500);
        timerSimulacion.start();
        // debug
        showOverlayEstatico = true;
    }

    public void draw() {
        dibujarFondo();
        labels.values().forEach(Label::draw);
        /*
        se debe asegurar que los indSlotIAXX se dibujen siempre por encima de los indSlotXX, para
        ello los filtramos de la lista general y luego dibujamos encima solamente los indSlotIAXX
        */
        indicators.values().stream().filter(ind -> !ind.getCode().contains("IA")).forEach(Indicator::draw);
        indicators.values().stream().filter(ind -> ind.getCode().contains("IA")).forEach(Indicator::draw);
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
        else if (key == 'd') {
            Indicator ind;
            for (int i = 0; i < 12; i++) {
                String s = "indSlotVacio" + String.format("%02d", i + 1);
                ind = indicators.get(s);
                //if (ind != null) ind.setOn(!ind.isOn());
            }
        }
    }

}
