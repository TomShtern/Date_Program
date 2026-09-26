# Plan 12: Service Construction and Contract Clarity

> **Date:** 2026-04-10
> **Wave:** 2
> **Priority:** Medium-Low
> **Parallel-safe with:** none recommended
> **Status:** Planned

---

## Objective

Clarify how selected runtime services and validators communicate supported construction modes and edge-condition rules so callers are not forced to infer behavior from overload shapes or vague messages.

## Issues addressed

| Issue ID | Summary                                                              |
|----------|----------------------------------------------------------------------|
| 6.2      | `ServiceRegistry.Builder` exposes an oversized setter surface        |
| 9.1      | `ActivityMetricsService` exposes a confusing construction mode split |
| 16.1     | `ActivityMetricsService` constructor Javadoc is misleading           |
| 16.2     | Weight-validation errors do not describe the accepted tolerance      |

## Primary source files and seams

- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/main/java/datingapp/core/metrics/ActivityMetricsService.java`
- `src/main/java/datingapp/core/AppConfigValidator.java`
- `src/main/java/datingapp/core/AppConfig.java`

## Boundary contract

### Primary edit owners

- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/main/java/datingapp/core/metrics/ActivityMetricsService.java`
- `src/main/java/datingapp/core/AppConfigValidator.java`

### Supporting read-only seams

- `src/main/java/datingapp/core/AppConfig.java`

### Escalate instead of expanding scope if

- builder cleanup starts turning into broader runtime/bootstrap redesign
- the change starts requiring `StorageFactory` or `ApplicationStartup` restructuring instead of staying inside contract clarity

## Primary verification slice

- `src/test/java/datingapp/core/ServiceRegistryTest.java`
- `src/test/java/datingapp/core/ActivityMetricsServiceTest.java`
- `src/test/java/datingapp/core/ActivityMetricsServiceDiagnosticsTest.java`
- `src/test/java/datingapp/core/ActivityMetricsServiceConcurrencyTest.java`
- `src/test/java/datingapp/core/AppConfigValidatorTest.java`
- `src/test/java/datingapp/core/AppConfigTest.java`

## Execution slices

### Slice A — narrow or group builder inputs where it reduces accidental complexity

- keep `ServiceRegistry` as the single composition root
- refactor `ServiceRegistry.Builder` to replace the current one-setter-per-field sprawl with grouped helper inputs such as `storageDependencies(...)`, `matchingDependencies(...)`, `profileDependencies(...)`, and `runtimePolicies(...)`, so the builder still owns composition while reducing the public setter surface materially
- acceptance criteria: remove the individual builder setters covered by those grouped helpers, keep validation/build ownership inside `ServiceRegistry.Builder`, and make the remaining public builder API small enough that issue 6.2 is no longer an oversized setter surface in practice

### Slice B — make constructor-mode semantics explicit

- document the canonical runtime path versus compatibility/test paths for `ActivityMetricsService`
- ensure the code and the docs tell the same story

### Slice C — improve contract messaging

- update weight-validation messages so developers can see the accepted tolerance without reading source

## Dependencies and orchestration notes

- Run this plan after the runtime/storage seams in Wave 2 start to stabilize so constructor/documentation cleanup describes settled behavior instead of moving targets.
- Treat `ServiceRegistry.java` as single-owner while this plan is active.

## Out of scope

- broad cross-cutting core-import cleanup (P13)
- runtime database lifecycle changes (P09)
- social/use-case constructor cleanup already owned by P04B