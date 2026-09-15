# Development Notes

## Working principles

- Keep `Sketch` as the explicit composition root and Processing boundary.
- Pass domain dependencies explicitly; use `ApplicationContext` only for access to the active Processing sketch.
- Use `ApplicationComponent` only for behavior that genuinely needs Processing APIs.
- Keep containers and configuration records passive.
- Use `Initializable` when startup has an explicit, order-sensitive action.
- Let managers own coordinated state, panel updaters project snapshots, controllers route behavior, and renderers draw.
- Preserve stable JSON control codes and backend codes across their mapping boundaries.

## Adding or changing controls

Control definitions live in JSON, but adding a control can also require changes to `UiComponentContainer`, `ControlManager`, input registration, interaction routing, panel updates, and `ControlRenderer` ordering. A code referenced through `Map.get()` is effectively required even if the Java type system cannot enforce it.

Buttons and Toggles need pointer-target registration and listeners. Labels and Indicators are passive render/update targets. Keep programmatic state changes guarded when a listener could call back into the same manager.

## Adding snapshot-driven UI

Prefer this path:

1. Expose the required backend provider/state through `SimulationContainer`.
2. Build it in `SimulationManager.initialize()` in dependency order.
3. Capture an immutable snapshot in `updateSnapshots()`.
4. Project it in a focused panel updater during `updateControlsIfNeeded()`.
5. Store only data that a later renderer needs in `UiStateContainer`.

Do not query mutable backend systems from a renderer. The snapshot boundary keeps a frame internally consistent and makes backend/UI ownership clear.

## Fields versus setup locals

Before adding a field to `Sketch`, ask whether `draw()` or a Processing callback needs the object after setup. If not, keep it local. Local composition objects make lifecycle ownership visible and reduce the sketch's persistent surface. Runtime collaborators keep their own constructor-injected dependencies, so local variables do not imply premature disposal.

## Validation

The standard build check is `mvn test`. There is currently no `src/test` suite, so this principally validates dependency resolution and compilation. A manual UI run should additionally verify assets, rendering order, pointer hit areas, play/speed shortcuts, selections, and cooling state transitions in a graphical environment.

Before completing an architecture refactor, search documentation and source for names that were renamed or deleted so the written model remains aligned with the code.

## Reference template

`main.TemplateSketch` is intentionally preserved unchanged as a reference for controls beyond Button, Label, and Toggle. `TemplateSketchConfig` and the template-specific JSON/tooltip files support that reference. Do not treat these types as active application architecture, and do not remove them simply because `Launcher` starts `Sketch`.
