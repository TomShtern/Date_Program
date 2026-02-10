# Architecture Audit — Summary

**Date:** 2026-02-07 | **126 main + 56 test files** | **34,289 code lines** | **9 layer violations** | **4 god classes**

---

## Top 5 Findings

### 1. ViewModel→Storage Layer Violations (6 files — CRITICAL)
Six of eight ViewModels bypass the service layer, importing `core.storage.*` directly. This breaks MVVM, couples UI to data access, and makes testing difficult. Three of these also run DB queries on the FX thread, causing UI freezes.

**Fix**: Route all data access through services. Add ArchUnit test to prevent regression.
**Effort**: 6h | **Risk**: Medium

### 2. Flat core/ Package (44 files — HIGH)
All domain models, services, utilities, enums, and config live in one flat package. Navigation is painful, module boundaries are invisible, and unrelated classes are coupled.

**Fix**: Split into 6 sub-packages: `matching/`, `messaging/`, `profile/`, `safety/`, `stats/`, `daily/`.
**Effort**: 8h | **Risk**: High (import path changes only — zero logic changes)

### 3. Logging Helper Duplication (23 files — MEDIUM)
23 files contain identical private `logInfo()`, `logWarn()`, `logError()` methods — ~2,645 LOC of duplicated boilerplate caused by PMD's GuardLogStatement rule.

**Fix**: Create a `LoggingSupport` interface with default methods. Each class implements it.
**Effort**: 3h | **Risk**: Low

### 4. ServiceRegistry God-Class (472 LOC — HIGH)
26+ fields, 25-param constructor, imports `storage/` from `core/` — a textbook god-class. The inner `Builder.buildH2()` method is 100+ LOC of inline JDBI wiring.

**Fix**: Extract `StorageFactory` to `storage/` package. Move `AppBootstrap` and `ConfigLoader` out of `core/`.
**Effort**: 4h | **Risk**: Medium

### 5. Dead / Orphaned Code (5 files — LOW)
`PurgeService` has zero imports. `SoftDeletable` has 2 implementors. `EnumSetUtil` has 1 consumer. `ErrorMessages` has 3 consumers. All are merge/delete candidates.

**Fix**: Delete, inline, or merge each. Total: -5 files.
**Effort**: 1h | **Risk**: Low

---

## Prioritized Action List

### Do First (Week 1–2) — Quick Wins
| Action | Impact | Effort | Files Changed |
| --- | --- | --- | --- |
| Delete `PurgeService` (dead code) | -1 file | 5 min | 1 |
| Merge `CliConstants + CliUtilities → CliSupport` | -1 file | 15 min | 11 |
| Inline `SoftDeletable` into User + Match | -1 file | 15 min | 3 |
| Inline/generalize `EnumSetUtil` | -1 file | 10 min | 2 |
| Merge `ErrorMessages → ValidationService` | -1 file | 10 min | 4 |
| Move `MODULE_OVERVIEW.md` to `docs/` | Cleaner source | 2 min | 2 |
| Add shared `handleBack()` to `BaseController` | -6 duplicates | 10 min | 7 |
| Create `LoggingSupport` mixin interface | -2,645 LOC duplication | 90 min | 24 |
| Genericize 6 dealbreaker edit methods | -5 methods | 30 min | 1 |
| Clean root-level workspace artifacts | Cleaner repo | 15 min | 20+ |

### Do Next (Week 3–4) — Architecture Fixes
| Action | Impact | Effort | Files Changed |
| --- | --- | --- | --- |
| Fix FX-thread DB queries (3 ViewModels) | No more UI freezes | 3h | 3+3 |
| Route ViewModels through services only | Clean MVVM | 3h | 6+services |
| Move AppBootstrap + ConfigLoader out of core/ | Clean layering | 1h | 5 |
| Extract StorageFactory from ServiceRegistry | -100 LOC god-class | 2h | 3 |
| Add 3 ArchUnit layer-violation tests | Permanent guard | 1h | 1 test file |

### Do Later (Week 5–6) — Structural Improvements
| Action | Impact | Effort | Files Changed |
| --- | --- | --- | --- |
| Split core/ into 6 sub-packages | Clear module boundaries | 8h | 182+ |
| Inject AppConfig via constructor (8 files) | Testable config | 2h | 8 |
| Extract large methods (>100 LOC) | Reduced complexity | 2h | 4 |
| Nest standalone enums into User.java | -3 files | 1h | 80+ |

---

## Key Metrics After Refactoring (Projected)

| Metric | Before | After | Change |
| --- | --- | --- | --- |
| Main files | 126 | ~120 | -6 |
| Layer violations | 9 | 0 | -9 |
| God classes (>400 LOC, 15+ fields) | 4 | 0 | -4 |
| Duplicated LOC (logging + other) | ~3,000 | ~200 | -93% |
| Flat core/ files | 44 | ~12 | -73% (rest in sub-packages) |
| FX-thread DB queries | 3 VMs | 0 | -3 |
| Dead code files | 3 | 0 | -3 |
| ArchUnit guard tests | 0 | 3 | +3 |
| Total estimated effort | — | ~27h | Over 6 weeks |

---

## Root Causes

1. **Organic growth** without package planning → flat core/ with 44 files
2. **PMD rule workaround** copied instead of shared → 23 files with logging helpers
3. **Expedient shortcuts** in ViewModels → direct storage access (bypassing services)
4. **Manual DI** at scale → ServiceRegistry god-class with 25-param constructor
5. **No automated layer tests** → violations accumulated undetected

## Prevention

1. **ArchUnit tests** — Enforce layer rules in CI
2. **`LoggingSupport` interface** — Standard pattern for new files
3. **Package-info.java** — Document module boundaries
4. **Constructor injection only** — No static config patterns
5. **Code review checklist** — Check for storage imports in ViewModels

---

*See [report.md](report.md) for full details, file-by-file analysis, and implementation roadmap.*
