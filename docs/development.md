# Development Guidelines

## Design Principles

- Single Responsibility Principle.
- Small incremental refactors.
- Components own a single responsibility.
- Processing Sketch coordinates the application.
- All UI components extend `ApplicationComponent`.
- Components participating in startup implement `Initializable`.

**Only active application components extend `ApplicationComponent`**
```text
Passive objects such as containers, state holders, configuration
classes and data transfer objects remain independent and do not
require access to the application context.
```

## Naming Conventions

Managers own shared state.

Controllers implement behavior.

Renderers draw.

Models store data.

Providers expose read-only resources.