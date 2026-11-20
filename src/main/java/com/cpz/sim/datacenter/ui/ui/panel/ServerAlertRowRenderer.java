package com.cpz.sim.datacenter.ui.ui.panel;

import com.cpz.processing.controls.controls.floatinglist.FloatingListPanel;
import com.cpz.processing.controls.controls.geometry.ControlMeasure;
import com.cpz.processing.controls.controls.geometry.ResolvedBounds;
import com.cpz.processing.controls.core.overlay.notification.NotificationSeverity;
import com.cpz.processing.controls.core.overlay.notification.NotificationStyle;
import com.cpz.processing.controls.core.util.FontLoader;
import com.cpz.processing.controls.core.util.JsonConfigSupport;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.datacenter.ui.ui.ServerAlertPresentation;
import com.cpz.sim.datacenter.ui.ui.SimulationTimeFormat;
import processing.core.PApplet;
import processing.data.JSONObject;

import static processing.core.PConstants.CENTER;
import static processing.core.PConstants.LEFT;
import static processing.core.PConstants.RIGHT;

/** Renders the alert fields inside the viewport supplied by controls. */
final class ServerAlertRowRenderer implements FloatingListPanel.RowRenderer<ServerAlertRowRenderer.Row> {
    private static final String CONTEXT = "alert rows";
    private static final String ELLIPSIS = "…";

    record Row(ServerLocation location, String locationText, NotificationSeverity severity,
               String condition, String observedTime) {
        static Row from(ServerAlertPresentation alert) {
            return new Row(alert.location(), alert.location().code(), alert.severity(), alert.conditionText(),
                    SimulationTimeFormat.daysHoursMinutes(alert.observedStateSinceSeconds()));
        }
    }

    private final PApplet sketch;
    private final NotificationStyle notifications;
    private final Style style;
    private final FontLoader.FontResolver font;

    ServerAlertRowRenderer(PApplet sketch, NotificationStyle notifications, JSONObject presentation, String path) {
        this.sketch = sketch;
        this.notifications = notifications;
        style = Style.load(presentation.getJSONObject("rowRenderer"), path + " -> rowRenderer");
        String fontPath = presentation.hasKey("style") && !presentation.isNull("style")
                ? JsonConfigSupport.getOptionalNonBlankString(presentation.getJSONObject("style"),
                        "font", path + " -> style", CONTEXT)
                : null;
        font = fontPath == null ? null : FontLoader.resolver(fontPath, CONTEXT, path);
    }

    @Override
    public void draw(Row item, ResolvedBounds row) {
        int accent = switch (item.severity()) {
            case ERROR -> notifications.getErrorAccentColor();
            case WARNING -> notifications.getWarningAccentColor();
            case SUCCESS -> notifications.getSuccessAccentColor();
            case INFO -> notifications.getInfoAccentColor();
        };
        float padding = Math.min(style.horizontalPadding().resolve(sketch.height), row.width() / 2);
        float left = row.x() + padding;
        float right = row.x() + row.width() - padding;
        float lineSpacing = Math.min(style.lineSpacing().resolve(sketch.height), row.height() / 2);
        float center = row.y() + row.height() / 2;
        float firstLine = center - lineSpacing / 2;
        float secondLine = center + lineSpacing / 2;
        float stripeWidth = Math.min(style.accentWidth().resolve(sketch.height), padding);
        float bandHeight = Math.max(0, row.height() - style.rowGap().resolve(sketch.height));
        String severity = item.severity().name();

        sketch.noStroke();
        sketch.fill(style.backgroundColor());
        sketch.rect(row.x(), row.y(), row.width(), bandHeight);
        sketch.fill(accent);
        sketch.rect(row.x(), row.y(), stripeWidth, bandHeight);
        float textSize = Math.min(style.textSize().resolve(sketch.height), row.height() / 2);
        if (font == null) sketch.textSize(textSize);
        else sketch.textFont(font.load(sketch, textSize), textSize);

        float rightColumn = Math.max(sketch.textWidth(item.observedTime()), sketch.textWidth(severity));
        float leftWidth = right - rightColumn - style.columnGap().resolve(sketch.height) - left;

        sketch.textAlign(LEFT, CENTER);
        sketch.fill(notifications.getTextColor());
        sketch.text(fit(item.locationText(), leftWidth), left, firstLine);
        sketch.fill(style.secondaryTextColor());
        sketch.text(fit(item.condition(), leftWidth), left, secondLine);
        sketch.textAlign(RIGHT, CENTER);
        sketch.text(fit(item.observedTime(), right - left), right, firstLine);
        sketch.fill(accent);
        sketch.text(fit(severity, right - left), right, secondLine);
    }

    private String fit(String text, float width) {
        if (sketch.textWidth(text) <= width) return text;
        float remaining = width - sketch.textWidth(ELLIPSIS);
        if (remaining < 0) return "";
        char[] characters = text.toCharArray();
        int end = text.length();
        while (sketch.textWidth(characters, 0, end) > remaining) {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end) + ELLIPSIS;
    }

    record Style(int backgroundColor, int secondaryTextColor, ControlMeasure horizontalPadding,
                 ControlMeasure columnGap, ControlMeasure rowGap, ControlMeasure accentWidth,
                 ControlMeasure textSize, ControlMeasure lineSpacing) {
        static Style load(JSONObject json, String path) {
            return new Style(required(JsonConfigSupport.getOptionalColor(json, "backgroundColor", path), path, "backgroundColor"),
                    required(JsonConfigSupport.getOptionalColor(json, "secondaryTextColor", path), path, "secondaryTextColor"),
                    measure(json, path, "horizontalPadding", 0), measure(json, path, "columnGap", 0),
                    measure(json, path, "rowGap", 0), measure(json, path, "accentWidth", 0),
                    measure(json, path, "textSize", Float.MIN_VALUE), measure(json, path, "lineSpacing", Float.MIN_VALUE));
        }

        private static ControlMeasure measure(JSONObject json, String path, String key, float minimum) {
            var value = required(JsonConfigSupport.getOptionalStyleMeasure(json, key, path, CONTEXT), path, key);
            if (value.value() < minimum) throw new IllegalArgumentException("Invalid measure " + key + " in " + path);
            return value;
        }

        private static <T> T required(T value, String path, String key) {
            if (value == null) throw new IllegalArgumentException("Missing " + key + " in " + path);
            return value;
        }
    }
}
