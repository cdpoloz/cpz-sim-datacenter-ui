# CPZ MVVM Processing Template

![Java](https://img.shields.io/badge/Java-17+-orange)
![Processing](https://img.shields.io/badge/Processing-4.5.x-blue)
![Status](https://img.shields.io/badge/status-active-brightgreen)
![License](https://img.shields.io/badge/license-Apache--2.0-lightgrey)
[![GitHub](https://img.shields.io/badge/GitHub-cdpoloz-181717?logo=github)](https://github.com/cdpoloz)

`cpz-mvvm-processing-template` is a Maven-based Processing desktop template
that shows how to consume `cpz-mvvm-processing-controls` from application code.

The template keeps the application side explicit:

- `Launcher` loads `data/config.properties` and starts the Processing sketch.
- `TemplateSketchConfig` applies window settings and loads controls from JSON.
- `TemplateSketch` owns sketch setup, event callbacks, typed control maps,
  input-layer registration, overlay rendering, and control behavior.
- `MainInputLayer` routes normalized framework input events to the controls
  selected by the sketch.

Controls are configured in `data/config/template-sketch.json` and loaded with
`ControlConfigLoader`. The current sketch includes buttons, checkbox,
dropdown, label, numeric field, radio group, slider, text field, toggle, and
tooltip examples.

## Requirements

- JDK 17 or newer.
- Maven.
- A desktop environment capable of running Processing.

Processing is resolved through Maven. The project does not require a local
Processing installation, copied JARs, `systemPath`, or a manual `lib/`
classpath.

## Maven Coordinates

The current `pom.xml` declares:

- `org.processing:core:4.5.2`
- `io.github.cdpoloz:cpz-mvvm-processing-controls:0.5.0`
- `io.github.cdpoloz:cpz-utils:0.2.3`
- `org.jetbrains:annotations:26.0.2`

The project artifact is:

```xml
<groupId>com.cpz</groupId>
<artifactId>cpz-mvvm-processing-template</artifactId>
<version>0.1.0</version>
```

The compiler release is Java 17.

## Run And Verify

From an IDE, import the project as Maven and run:

```text
com.cpz.processing.template.main.Launcher
```

From the command line, compile or test with Maven:

```bash
mvn --batch-mode --no-transfer-progress compile
mvn --batch-mode --no-transfer-progress test
```

If your Maven environment resolves the Maven Exec plugin and you have a
desktop display available, you can launch the sketch with:

```bash
mvn --batch-mode --no-transfer-progress compile exec:java -Dexec.mainClass=com.cpz.processing.template.main.Launcher
```

`Launcher` reads `data/config.properties`, sets the default locale to
`en-US`, configures logging shutdown, and starts `TemplateSketch` with
`PApplet.main(TemplateSketch.class)`.

## Project Structure

```text
pom.xml
README.md
LICENSE
NOTICE

data/
  config.properties
  config/
    template-sketch.json
    custom-area-tooltip.json
  font/
    JetBrainsMono.ttf
    OFL.txt
  img/
    windowIcon.png

src/main/java/com/cpz/processing/template/
  config/
    ConfigLog.java
    TemplateSketchConfig.java
  input/
    MainInputLayer.java
  logging/
    Log.java
    LogFormatter.java
    LogMessage.java
  main/
    Launcher.java
    TemplateSketch.java

src/main/resources/
src/test/java/
src/test/resources/
```

`src/main/resources`, `src/test/java`, and `src/test/resources` exist in the
Maven layout, but the current template has no resource files or test classes in
those folders.

## Runtime Configuration

`data/config.properties` is the launcher/runtime configuration file. It
currently provides:

- `window.title`
- `window.icon`
- `sketch.width`
- `sketch.height`
- `sketch.fps`
- `sketch.smoothing`

`TemplateSketchConfig.settings(...)` uses these properties to set the window
icon, size, renderer, and smoothing. The sketch uses Processing `P2D`.

`TemplateSketchConfig.initialSetup(...)` sets the frame rate and window title.

## JSON Control Configuration

The main control configuration file is:

```text
data/config/template-sketch.json
```

`TemplateSketchConfig.setupControls(...)` loads it with:

```java
String templateConfigPath = "data" + File.separator + "config" + File.separator + "template-sketch.json";
ControlConfigLoader loader = new ControlConfigLoader(sk, overlayManager, inputManager);
Map<String, Control> controls = loader.load(templateConfigPath);
```

The JSON has two top-level sections:

- `tooltipStyles`: shared tooltip style definitions.
- `controls`: the list of controls to create.

The current `controls` array contains these control codes:

- `btnTemplate`
- `chkTemplate`
- `ddTemplate`
- `lblTemplate`
- `nfTemplate`
- `rgTemplate`
- `sldTemplate`
- `tfTemplate`
- `tglTemplate`

Each control entry defines its `type`, `code`, geometry, enabled/visible state,
type-specific properties, and an optional `style` block. Some controls also
define a `tooltip` block.

## Custom Fonts

The template includes JetBrains Mono at:

```text
data/font/JetBrainsMono.ttf
```

`template-sketch.json` references this font in several control styles, for
example labels, buttons, text fields, numeric fields, dropdowns, radio groups,
and tooltip styles:

```json
"font": "data/font/JetBrainsMono.ttf"
```

The font license is included at `data/font/OFL.txt`.

## Accessing Loaded Controls

`TemplateSketch.setup()` keeps the full loaded map and also builds typed maps:

```java
private Map<String, Control> controls;
private Map<String, Button> buttons;
private Map<String, Checkbox> checkboxes;
private Map<String, DropDown> dropdowns;
private Map<String, Label> labels;
private Map<String, NumericField> numericfields;
private Map<String, RadioGroup> radiogroups;
private Map<String, Slider> sliders;
private Map<String, TextField> textfields;
private Map<String, Toggle> toggles;
```

The typed maps are created with helper methods in `TemplateSketchConfig`, such
as `filterButtons(controls)` and `filterLabels(controls)`. Use the control
`code` from JSON as the map key:

```java
Button templateButton = buttons.get("btnTemplate");
Label templateLabel = labels.get("lblTemplate");
```

The sketch wires behavior explicitly. For example, button clicks call
`TemplateSketch.btnClicked(...)`, which updates `lblTemplate`.

## InputManager Integration

`TemplateSketch` creates one `InputManager` and one app-owned
`MainInputLayer`:

```java
inputManager = new InputManager();
MainInputLayer mainInputLayer = new MainInputLayer(0);
```

The sketch registers pointer targets for interactive controls:

```java
buttons.values().forEach(btn -> mainInputLayer.addPointerTarget(btn::handlePointerEvent));
checkboxes.values().forEach(chk -> mainInputLayer.addPointerTarget(chk::handlePointerEvent));
dropdowns.values().forEach(dd -> mainInputLayer.addPointerTarget(dd::handlePointerEvent));
numericfields.values().forEach(nf -> mainInputLayer.addPointerTarget(nf::handlePointerEvent));
radiogroups.values().forEach(rg -> mainInputLayer.addPointerTarget(rg::handlePointerEvent));
sliders.values().forEach(sld -> mainInputLayer.addPointerTarget(sld::handlePointerEvent));
textfields.values().forEach(tf -> mainInputLayer.addPointerTarget(tf::handlePointerEvent));
toggles.values().forEach(tgl -> mainInputLayer.addPointerTarget(tgl::handlePointerEvent));
```

It registers keyboard targets for controls that handle keyboard input:

```java
numericfields.values().forEach(nf -> mainInputLayer.addKeyboardTarget(nf::handleKeyboardEvent));
radiogroups.values().forEach(rg -> mainInputLayer.addKeyboardTarget(rg::handleKeyboardEvent));
textfields.values().forEach(tf -> mainInputLayer.addKeyboardTarget(tf::handleKeyboardEvent));
```

Finally, the layer is registered in the input manager:

```java
inputManager.registerLayer(mainInputLayer);
```

Processing mouse callbacks create `PointerEvent` instances and dispatch them
through `InputManager`. Keyboard callbacks are delegated through
`ProcessingKeyboardAdapter`.

## OverlayManager Integration

`TemplateSketch` creates an `OverlayManager` before loading controls:

```java
overlayManager = new OverlayManager();
controls = TemplateSketchConfig.setupControls(this, overlayManager, inputManager);
```

The same `OverlayManager` is passed to `ControlConfigLoader`, so controls that
need overlays, such as dropdowns, can use the shared overlay stack.

At the end of `draw()`, after drawing the regular controls and custom area, the
sketch renders active overlays:

```java
overlayManager.getActiveOverlays().forEach(entry -> entry.getRender().run());
```

This final render step is required so dropdown and tooltip overlays appear on
top of the normal sketch content.

## Tooltip Flow

The template demonstrates two tooltip styles of usage:

- A control tooltip configured in `template-sketch.json`.
- A custom rectangular `TooltipArea` loaded from
  `data/config/custom-area-tooltip.json`.

In `template-sketch.json`, shared tooltip styles live under `tooltipStyles`:

```json
"tooltipStyles": {
  "dark": {
    "backgroundColor": "#E61B1F26",
    "textColor": "#FFFFFFFF",
    "borderColor": "#668A94A6",
    "font": "data/font/JetBrainsMono.ttf",
    "textSize": 14.0,
    "textPadding": 10.0,
    "offset": 10.0,
    "cornerRadius": 8.0,
    "strokeWeight": 1.0
  }
}
```

The `btnTemplate` control defines its own tooltip and reuses that style with
`styleRef`:

```json
{
  "type": "button",
  "code": "btnTemplate",
  "tooltip": {
    "text": "Tooltip over Button object",
    "enabled": true,
    "styleRef": "dark"
  }
}
```

The JSON describes tooltip content and style. The sketch still has to register
the object as a tooltip target so the tooltip controller tracks hover/input for
that object.

The current project creates the controller in
`TemplateSketchConfig.setupTooltips(...)`:

```java
TooltipOverlayController tooltips = new TooltipOverlayController(sk, overlayManager);
```

It registers the custom area from `custom-area-tooltip.json`:

```java
TooltipArea customTooltipArea = new TooltipArea(750, 350.0f, 200.0f, 100.0f)
        .setTooltip(TooltipFactory.loadFromJson(sk, "data/config/custom-area-tooltip.json"));
tooltips.registerTarget(customTooltipArea);
```

It also registers tooltip-enabled controls from the loaded control map:

```java
tooltips.registerTarget((Button) controls.get("btnTemplate"));
tooltips.registerTarget((Slider) controls.get("sldTemplate"));
```

If you register the button from the typed `buttons` map in the sketch, use the
same pattern with a null guard:

```java
Button templateButton = buttons.get("btnTemplate");
if (templateButton != null) {
    tooltips.registerTarget(templateButton);
}
```

`TemplateSketch.setup()` then registers the tooltip input layer with a higher
priority than the main layer:

```java
inputManager.registerLayer(new TooltipInputLayer(1000, tooltips));
```

The complete tooltip path is:

```text
template-sketch.json tooltip/tooltipStyles
  -> ControlConfigLoader creates tooltip-capable controls
  -> TooltipOverlayController registers targets
  -> TooltipInputLayer receives pointer input
  -> OverlayManager renders active tooltip overlays at the end of draw()
```

## Extending The Template

To add a new JSON-backed control:

1. Add a new object to the `controls` array in `data/config/template-sketch.json`.
2. Give it a unique `code`.
3. Add geometry and type-specific fields required by that control type.
4. Add a `style` block or reuse the defaults supported by
   `cpz-mvvm-processing-controls`.
5. If the control should react to input, make sure `TemplateSketch.setup()`
   registers it in `MainInputLayer`.
6. If the control needs keyboard input, register `handleKeyboardEvent`.
7. If the control should show a tooltip, add a `tooltip` block in JSON and
   register the loaded control with `TooltipOverlayController`.
8. Access the control from the relevant typed map and attach sketch-specific
   behavior or listeners.

Example:

```java
Button button = buttons.get("myButtonCode");
if (button != null) {
    button.setClickListener(() -> labels.get("lblTemplate").setText("Clicked myButtonCode"));
}
```

Keep behavior in the sketch or application configuration code. Keep structure,
position, style, and tooltip text in JSON when you want those parts to be data
driven.

## License

`cpz-mvvm-processing-template` is released under the Apache License, Version
2.0. See `LICENSE`.

JetBrains Mono is distributed under the SIL Open Font License 1.1. See
`data/font/OFL.txt`.

## Related Project

[cpz-mvvm-processing-controls](https://github.com/cdpoloz/cpz-mvvm-processing-controls)

## Author

**Carlos Polo Zamora**  
GitHub: https://github.com/cdpoloz  
Alias: CPZ / cepezeta / cdpoloz
