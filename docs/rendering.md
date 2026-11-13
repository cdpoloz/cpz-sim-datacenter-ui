# Rendering Flow

Rendering is performed every Processing frame after all required state updates. The order is part of the visual contract.

## Update before draw

`Sketch.draw()` polls the simulation clock, refreshes snapshots if invalidated, expands the shared temperature color scale, and then updates the header, notifications, and invalidated panel controls. The header and notification timestamps both format `healthSnapshot.elapsedSeconds()` as `00d hh:mm`; neither subtracts a tick nor assumes a one-minute tick. This guarantees that one frame never mixes a newly advanced engine with old panel controls.

Snapshot capture order is energy, temperature, health, then the combined operational snapshot. The combined snapshot depends on the first three. Panel update order is selected rack, selected aisle, then room. The selected aisle update also supplies the temperature list used by the gradient renderer later in the same frame.

## Layer order

```text
top/front    active control overlays, including rack cooling tooltip
             static ui_overlay.png
             selected hot-aisle temperature gradient
             buttons and toggles
             rack/slot/aisle indicators and labels
             six static background images
bottom/back  COLOR_BACKGROUND clear
```

In execution order, `StaticUiRenderer.drawBackground()` clears and draws the background image list; `ControlRenderer.draw()` draws each control group; `SelectedHotAisleTemperatureGradientRenderer.draw()` adds the dynamic heat shape; and `StaticUiRenderer.drawOverlay()` draws the full-canvas static overlay before running active overlay callbacks.

Moving the gradient before controls or moving the static overlay earlier would change masking and visual composition even if all coordinates stayed the same.

## Temperature colors

`TemperatureRangeCalculator` uses the backend ambient temperature as the minimum. For each server, it estimates a maximum equilibrium temperature as ambient plus maximum power divided by heat dissipation. A server-specific heat-dissipation value wins; otherwise the global temperature-system value is used. The highest estimate is rounded up and becomes the initial scale maximum. This estimate is not a physical maximum: room-air heating and temperature-driven input can exceed it. `TemperatureScaleUpdater` expands the bounds outward to multiples of 10 °C when any server temperature in the latest snapshot exceeds the current bounds. Bounds do not shrink during the session, so cooling does not continually change the color meaning. The six room legend labels and all panel color mappings use the expanded bounds in the same frame; health thresholds and hysteresis are unaffected.

Rack, slot, aisle, maximum-server, and gradient colors map temperatures into the same interval, clamp the factor to `[0, 1]`, and interpolate between `COLOR_MIN_TEMPERATURE` and `COLOR_MAX_TEMPERATURE`. The room aisle fill uses the midpoint of average and maximum aisle temperature, plus temperature-derived alpha, to expose both typical load and hot spots.

Offline/empty/hotspot rack conditions can override the ordinary interpolated rack color. Labels additionally use health thresholds to distinguish normal, warning, and alert temperatures.

The room hotspot override is driven by any online server in the rack whose
health snapshot has `thermalAlertLevel() == CRITICAL`, not by the average
temperature exceeding the color-scale maximum. The backend's evaluated level
also drives notification severity and Global Overview SYSTEM STATUS; see
[Health and alerts](health-alerts.md). Label color scales remain presentation
details and do not evaluate critical health.

## Selected-aisle gradient

The selected aisle updater calculates one representative temperature per rack row. For shared aisles, each row value is the average of the representative temperatures for the same rack code in both columns. Wall aisles contain one column.

The renderer uses normalized geometry from `config.properties`, scaled by current canvas width/height. It draws horizontal lines that widen down the aisle. The first segment uses the first row's color; each later segment interpolates vertically between adjacent row colors. Finally, it draws the configured border. A null or empty temperature list suppresses the gradient during incomplete initialization.

Processing-specific calls such as `pushStyle()`, `map()`, `stroke()`, `line()`, `image()`, and canvas dimensions require an active `PApplet`, so renderers access `Sketch` through `ApplicationContext` and run from `draw()` on the sketch thread.

## Rack cooling tooltip

The selected rack drawing and its cooling fan share a dynamic tooltip, shown only
for racks with integrated cooling. `TooltipInputLayer` observes pointer events
without consuming them. After controls update, `TooltipOverlayController.refresh()`
keeps an open tooltip synchronized with the backend snapshot and live equipment
state even if the pointer stays still. Overlays draw above server indicators and
the static foreground. The former six permanent cooling labels are removed.
