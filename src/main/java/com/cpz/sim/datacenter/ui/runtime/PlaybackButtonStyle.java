package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.button.state.ButtonViewState;
import com.cpz.processing.controls.controls.button.style.ButtonStyle;
import com.cpz.processing.controls.controls.geometry.ControlMeasure;
import com.cpz.processing.controls.controls.toggle.config.ToggleConfig;
import com.cpz.processing.controls.controls.toggle.config.ToggleConfigLoader;
import com.cpz.processing.controls.controls.toggle.config.ToggleStyleConfig;
import com.cpz.processing.controls.controls.toggle.state.ToggleViewState;
import com.cpz.processing.controls.controls.toggle.style.ParametricToggleStyle;
import com.cpz.processing.controls.controls.toggle.style.render.SvgShapeRenderer;
import com.cpz.processing.controls.core.theme.ThemeSnapshot;
import com.cpz.processing.controls.core.util.JsonConfigSupport;
import processing.core.PApplet;

/** Applies the CLASSIC toggle style to the RUNTIME button's pointer state. */
final class PlaybackButtonStyle implements ButtonStyle {
    private static final String CONFIG_PATH = "data/config/toggles.json";
    private static final String TOGGLE_CODE = "tglPlay";

    private final RuntimeClock clock;
    private final ControlMeasure iconSize;
    private final ParametricToggleStyle style;

    PlaybackButtonStyle(PApplet sketch, RuntimeClock clock) {
        this.clock = clock;
        var config = loadConfig(sketch);
        iconSize = config.getBounds().height();
        var configured = config.getStyle();
        var colors = new ToggleStyleConfig();
        colors.stateColors = configured.getStateColors();
        colors.offFillOverride = configured.getOffFillOverride();
        colors.onFillOverride = configured.getOnFillOverride();
        colors.hoverFillOverride = configured.getHoverFillOverride();
        colors.pressedFillOverride = configured.getPressedFillOverride();
        colors.strokeOverride = configured.getStrokeOverride();
        colors.strokeColor = configured.getStrokeColor();
        colors.strokeWidth = configured.getStrokeWidth() == null ? 0f : configured.getStrokeWidth();
        colors.strokeWidthHover = configured.getStrokeWidthHover() == null ? 0f : configured.getStrokeWidthHover();
        colors.hoverBlendWithWhite = configured.getHoverBlendWithWhite();
        colors.pressedBlendWithBlack = configured.getPressedBlendWithBlack();
        colors.disabledAlpha = configured.getDisabledAlpha();
        colors.setShapeRenderer(new SvgShapeRenderer(sketch, configured.getRenderer().getPath()));
        style = new ParametricToggleStyle(colors);
    }

    private static ToggleConfig loadConfig(PApplet sketch) {
        var root = JsonConfigSupport.loadRequiredObject(sketch, CONFIG_PATH, "playback toggle");
        var controls = JsonConfigSupport.getRequiredArray(root, "controls", CONFIG_PATH);
        for (int i = 0; i < controls.size(); i++) {
            var entry = controls.getJSONObject(i);
            if (TOGGLE_CODE.equals(entry.getString("code"))) {
                var config = new ToggleConfigLoader(sketch).loadFromJson(entry, CONFIG_PATH);
                if (config.getTotalStates() == 2 && config.getStyle() != null && config.getStyle().getRenderer() != null) {
                    return config;
                }
                break;
            }
        }
        throw new IllegalArgumentException("Missing two-state SVG toggle " + TOGGLE_CODE + " in " + CONFIG_PATH);
    }

    @Override
    public void render(PApplet sketch, ButtonViewState state, ThemeSnapshot theme) {
        float side = Math.min(iconSize.resolve(sketch.height), Math.min(state.width(), state.height()));
        style.render(sketch, new ToggleViewState(state.x(), state.y(), side, side, clock.running() ? 1 : 0,
                2, state.hovered(), state.pressed(), state.enabled()), theme);
    }

    @Override
    public ThemeSnapshot getThemeSnapshot() {
        return style.getThemeSnapshot();
    }
}
