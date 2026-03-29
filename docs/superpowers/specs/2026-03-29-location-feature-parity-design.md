# Location Feature Parity Design

**Date:** 2026-03-29
**Status:** Approved in chat

## Goal

Complete the location feature end to end across JavaFX, CLI, and REST API using the current repository architecture, while removing remaining raw-coordinate UX leaks and preserving the existing lat/lon storage model.

## Chosen approach

Use a parity-focused hybrid design:

- keep the current `LocationService` as the shared location engine
- keep programmatic JavaFX dialog construction, but extract location-dialog behavior into a focused helper for readability and testability
- mirror the same feature behavior in CLI and API
- keep persistence and internal use-case boundaries lat/lon based
- support API clients with location lookup and selection endpoints rather than requiring raw coordinates only

## Why this fits the codebase

- The UI layer already uses programmatic themed dialogs and helper utilities.
- `ProfileController` is already large, so feature completion should reduce—not increase—inline dialog complexity.
- `LocationService` already owns the built-in location dataset and core resolution behavior.
- The current REST layer still leaks coordinate-like display values, so parity requires adapter-level improvement there too.

## Functional requirements

### Shared parity behavior

All entry points must support the same location feature rules:

- available countries list
- searchable cities
- ZIP lookup
- reverse resolution from saved coordinates to a user-friendly selection/display state
- valid-but-unsupported ZIP fallback to an approximate supported location
- user-facing labels must be human-friendly, not raw coordinates, whenever supported location data exists

### JavaFX

- `ProfileController` must launch a location-selection flow that supports country, city, ZIP, preview, and fallback behavior
- existing saved location should prepopulate the dialog using reverse resolution
- remaining raw-coordinate copy in `profile.fxml` must be replaced with user-friendly wording
- profile preview and other display surfaces must use human-friendly labels

### CLI

- `ProfileHandler` must mirror the same location selection behavior used by JavaFX
- valid-but-unsupported ZIP should not dead-end the user; it should offer approximate fallback
- preview and display output should show friendly labels

### REST API

- add location metadata endpoints so API clients can build the same country/city/ZIP flow
- add a location resolution endpoint so clients can preview a selection and fallback behavior before saving
- extend profile update input to accept location selection data in addition to raw lat/lon for backward compatibility
- profile responses should expose human-friendly location display values instead of coarse coordinates

## Architectural changes

### Core

Extend `LocationService` with richer selection-oriented APIs, likely including:

- reverse-selection seed from saved coordinates
- selection resolution from city or ZIP input
- approximate fallback resolution for valid-but-unsupported ZIP codes
- shared display formatting for API/UI/CLI

### JavaFX

Add a focused helper class, likely `ui/screen/LocationSelectionDialog.java`, responsible for:

- building the dialog
- managing country/city/ZIP controls
- prepopulation
- preview/fallback state
- returning a resolved location choice back to `ProfileController`

### API

Keep `ProfileUseCases` lat/lon oriented unless a cleaner adapter boundary requires a small compatibility expansion. Prefer resolving API selection input inside the REST adapter using `LocationService` before calling the use case.

## Testing strategy

Use TDD and add failing tests first for:

- `LocationService` reverse-selection and fallback behavior
- `ProfileViewModel` friendly-label display and preview behavior
- JavaFX wiring/regression for location flow extraction
- CLI handler parity behavior
- REST API location lookup/resolve/profile-update parity behavior

## Non-goals

- database schema changes
- external geocoding services
- replacing the current storage model with structured persisted location entities
- forcing a full plan-literal FXML/controller/viewmodel wizard rewrite
