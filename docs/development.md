# Development Guidelines

## Design Principles

- Single Responsibility Principle.
- Small incremental refactors.
- Components own a single responsibility.
- Processing Sketch coordinates the application.
- All UI components extend `ApplicationComponent`.
- Components participating in startup implement `Initializable`.

## Naming Conventions

Managers own shared state.

Controllers implement behavior.

Renderers draw.

Models store data.

Providers expose read-only resources.