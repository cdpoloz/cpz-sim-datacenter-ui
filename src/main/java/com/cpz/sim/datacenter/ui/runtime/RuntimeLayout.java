package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.geometry.ControlBounds;
import com.cpz.processing.controls.controls.geometry.ResolvedBounds;
import java.util.EnumMap;
import java.util.Map;

/** Relative control geometry: x against width; y, width and height against height. */
public final class RuntimeLayout {
    public enum Area { HEADER, ROOM, SELECTED_AISLE, SELECTED_RACK, SUMMARY, TRENDS, ALERTS, FOOTER }
    private static final float GAP = .016f;
    // CLASSIC's room inset leaves clearance inside the outer frame (x ≈ 87 and 3754).
    private static final float BODY_INSET = 108f / 3840;
    private static final float BODY_BOTTOM = .900f;
    private final Map<Area, ControlBounds> areas = new EnumMap<>(Area.class);
    private final Map<Area, ControlBounds> titles = new EnumMap<>(Area.class);
    private final Map<Area, ControlBounds> contents = new EnumMap<>(Area.class);

    public RuntimeLayout(float width, float height) {
        float aspect = width / height;
        // Keep body panels inside the full-canvas frame, including on a wide desktop.
        float usable = Math.min(aspect * (1 - 2 * BODY_INSET), 1.98f);
        float left = (aspect - usable) / 2;
        float row = usable - 2 * GAP;
        float headerWidth = Math.min(usable, 1f);
        float headerLeft = (aspect - headerWidth) / 2;
        float available = 1 - 5 * GAP;
        float hh = available * .09f, mh = available * .59f;
        float mainY = 2 * GAP + hh, lowerY = mainY + mh + GAP;
        put(Area.HEADER, headerLeft, GAP, headerWidth, 130f / 1986 - GAP, aspect);
        areas.put(Area.ROOM, RoomMapArtwork.panelBounds(aspect));
        titles.put(Area.ROOM, RoomMapArtwork.bounds(aspect, 137, 173, 1675, 226));
        contents.put(Area.ROOM, RoomMapArtwork.bounds(aspect, 206, 399, 1664, 1076));
        put(Area.SELECTED_AISLE, left + row * .48f + GAP, mainY, row * .25f, mh, aspect);
        put(Area.SELECTED_RACK, left + row * .73f + 2 * GAP, mainY, row * .27f, mh, aspect);
        float lh = BODY_BOTTOM - lowerY;
        put(Area.SUMMARY, left, lowerY, row * .32f, lh, aspect);
        put(Area.TRENDS, left + row * .32f + GAP, lowerY, row * .42f, lh, aspect);
        put(Area.ALERTS, left + row * .74f + 2 * GAP, lowerY, row * .26f, lh, aspect);
        // Full-canvas footer artwork: normalized bounds from ui.svg/footerFondo.
        put(Area.FOOTER, .032f * aspect, .913f, .936f * aspect, .070f, aspect);
        contents.put(Area.FOOTER, ControlBounds.relative(.853f, .921f, .110f * aspect, .052f));
    }

    private void put(Area area, float x, float y, float w, float h, float aspect) {
        areas.put(area, relative(x, y, w, h, aspect));
        boolean inline = area == Area.HEADER || area == Area.FOOTER;
        titles.put(area, relative(x + GAP, y + GAP * .55f,
                (inline ? w * .52f : w) - 2 * GAP, GAP * 1.8f, aspect));
        contents.put(area, inline
                ? relative(x + w * .55f, y + GAP * .55f, w * .45f - GAP, GAP * 1.8f, aspect)
                : relative(x + GAP, y + GAP * 3.4f, w - 2 * GAP, h - GAP * 4.4f, aspect));
    }
    private static ControlBounds relative(float xHeight, float y, float w, float h, float aspect) {
        return ControlBounds.relative(xHeight / aspect, y, w, h);
    }
    /** CLASSIC anchors from toggles.json/buttonsPlay.json, scaled with the PNG.
     * Keep icons square even when the design canvas is scaled non-uniformly.
     * These bounds drive both drawing and pointer hit testing.
     */
    public ControlBounds playbackBounds(int index, float width, float height) {
        float[] x = {.8666768229f, .9356007813f, .9527882813f};
        float y = index == 0 ? .9471873112f : .9461797583f;
        float size = (index == 0 ? 50f : 46f) * Math.min(width / 3840f, height / 1986f);
        return ControlBounds.relative(x[index], y, size / height, size / height);
    }

    public float spacing(float height) { return GAP * height; }
    public ControlBounds bounds(Area area) { return areas.get(area); }
    public ControlBounds titleBounds(Area area) { return titles.get(area); }
    public ControlBounds contentBounds(Area area) { return contents.get(area); }
    public ControlBounds alertTriggerBounds(float width, float height) {
        var heading = titleBounds(Area.ALERTS).resolve(width, height);
        float buttonWidth = Math.min(.132f * height, heading.width() * .5f);
        return ControlBounds.relative((heading.x() + heading.width() - buttonWidth / 2) / width,
                (heading.y() + heading.height() / 2) / height, buttonWidth / height, .022f);
    }

    public ResolvedBounds resolve(Area area, float width, float height) {
        return bounds(area).resolve(width, height);
    }
}
