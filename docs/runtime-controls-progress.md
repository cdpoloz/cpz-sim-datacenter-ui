# Runtime controls: progress

1. **Startup and visual structure:** implemented. Visual acceptance remains pending.
2. **Layout and room map generation:** pending.
3. **Room simulation and state:** pending.
4. **Selected aisle:** pending.
5. **Selected rack:** pending.
6. **Cooling and functional equivalence**, including summary, trends and alerts: pending.
7. **Adaptation to other layouts:** pending.

## Hito 1

`Launcher` selects `RUNTIME` through the existing `simulation.source` JVM
property; `DEMO` remains the default and `DATASET` retains its replay path.
`RuntimeSketch` owns an independent Processing lifecycle. `RuntimeLayout` exposes
the shared area, title and content bounds; `RuntimeView` draws the shell and
constructs public library `Label` controls. The eight areas contain explicit
pending markers. No layout, assets, simulation, selection or graph data is
loaded. The 8 × 12 × 12 example is not a design constant.

Validated with Oracle JDK 26.0.1 and `mvn test`: 36 tests passed. The Maven
classpath was generated with `mvn --batch-mode --no-transfer-progress
dependency:build-classpath -Dmdep.outputFile=target/classpath.txt`. Runtime
opened in the graphical session. A screenshot was partly obscured by another
window, so complete visual acceptance is pending; the window manager also
resized the requested 1920 × 993 surface to 1920 × 1014. The classic mode
opened and entered setup without a reported exception before the eight-second
timeout; its full visual and interaction checks remain pending.

Manual acceptance: launch using the README command, bring the full window to
the foreground, check the eight titles and pending markers for clipping and
overlap at the configured resolution, then launch `DEMO` and verify its usual
selection and simulation controls. Replay instructions remain in
`dataset-replay.md`.

## Resources and dependencies

The runtime shell uses JetBrains Mono and the existing palette, but no classic
graphical JSON or image layers. The seven `ui_background*.png` layers provide
the classic background, room, aisle, rack, header, footer and overview; the
`ui_overlay.png` foreground provides its static overlay. Their decorative roles
are replaced by the shell's Processing frames, so new bitmaps are unnecessary
for this milestone. Classic button, toggle and indicator SVG/PNG assets provide
interactive and state visuals and are also unused here; assess reuse when those
features are implemented. Per-row `btnSelectedRackLeft/Right` files are not
needed for the new shell. `data/img/common/circle.svg` and `square.svg` exist;
they are reserved for later use and are unnecessary for these frames. All
resources remain available to the classic mode.

The consumed controls dependency is 0.9.12. Its local documentation and the
0.9.12 public `Label` API were inspected; no library blocker was identified.
No dependency or version was changed.

Next: complete visual acceptance, then start milestone 2 by inspecting the
layout input and generating the room map within the shared room bounds.

## Footer and relative geometry follow-up

The original geometry placed the footer inside its calculated canvas with a
bottom margin, but stored pixel bounds and did not update them after a resize.
It is therefore not established that the reported clipping originated in the
drawing. In the observed desktop session Processing reports the requested
1920 × 993 canvas was changed by the window manager to 1920 × 1014; the
decorated window measured 1920 × 1051 at y=29 on a 1920 × 1080 screen.

The runtime layout now stores public `ControlBounds.relative` values: x is
relative to surface width, while y and both dimensions use surface height.
The view resolves these bounds for decoration, passes the same measures to
labels, and recalculates them when the effective surface size changes. Startup
estimates the available screen area and decoration insets before creating the
runtime surface, scaling the configured size only if needed. This estimate is
not sufficient on the observed window manager: it still reports the resize
above. The footer's complete visibility on that desktop is not yet verified.

`mvn test` on JDK 26.0.1 passed 38 tests, including bounds and overlap tests
at three aspect ratios and relative-width resolution. Runtime opened after the
change; visual acceptance at both configured and smaller window sizes remains
pending. Further work must account for actual window-manager decorations and
maximization without resizing during Processing setup, which caused a reentrant
draw failure in an attempted approach. No library patch was identified.
