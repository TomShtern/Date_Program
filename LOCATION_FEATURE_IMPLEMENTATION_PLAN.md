# Location Feature Implementation Plan

**Status:** `COMPLETED`
**Created:** 2026-03-12
**Priority:** `HIGH`
**Estimated Effort:** `Completed on 2026-03-12`
**Risk Level:** `LOW`

---

## Executive Summary

Replace raw lat/lon coordinate input with **Country → City → ZIP fallback** flow, optimized for Israel-first deployment. Users never see or enter raw coordinates.

## Verified Implementation Outcome (2026-03-12)

This feature has now been implemented end to end and verified with `mvn spotless:apply verify`.

### What shipped

- ✅ Added `core/model/LocationModels.java` with immutable country, city, ZIP, and resolved-location records
- ✅ Added a single concrete `core/profile/LocationService.java` with built-in Israel-first location data
- ✅ Extended `core/profile/ValidationService.java` with ZIP validation support
- ✅ Wired `LocationService` through `core/ServiceRegistry.java`
- ✅ Updated `ui/viewmodel/ProfileViewModel.java` to show human-readable location labels when coordinates map to supported locations
- ✅ Replaced the JavaFX raw-coordinate dialog in `ui/screen/ProfileController.java` with country + city search + ZIP fallback UI
- ✅ Updated `app/cli/ProfileHandler.java` to use country + city / ZIP selection instead of raw coordinate input
- ✅ Added focused automated coverage in:
    - `src/test/java/datingapp/core/profile/LocationServiceTest.java`
    - `src/test/java/datingapp/core/ValidationServiceTest.java`
    - `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`

### Verified deviations from the original draft

The original draft was directionally correct, but the implementation intentionally used a simpler architecture to match the real codebase and avoid overengineering:

- ✅ **No schema changes were made** — the feature still stores only `lat` / `lon`, which was already sufficient
- ✅ **No extra repository/provider abstraction was added** — hardcoded location data lives directly in `LocationService`
- ✅ **No separate JavaFX FXML/controller wizard was added** — the new flow was integrated into the existing programmatic dialog in `ProfileController`
- ✅ **REST API shape stayed unchanged** — the app resolves city/ZIP to coordinates before saving, so existing profile update routes remain compatible

### Completion tracker

| Item                                    | Status | Notes                                               |
|-----------------------------------------|--------|-----------------------------------------------------|
| Plan verified against live architecture | ✅      | Conflicting draft assumptions were corrected        |
| Domain/location models implemented      | ✅      | `LocationModels` + `LocationService` added          |
| ZIP validation implemented              | ✅      | Added to `ValidationService`                        |
| Service registry integration completed  | ✅      | Shared `LocationService` available app-wide         |
| Profile ViewModel integration completed | ✅      | Human-readable display + resolved selection support |
| JavaFX profile dialog replaced          | ✅      | City/ZIP flow now hides raw coordinates             |
| CLI flow updated                        | ✅      | Country/city/ZIP prompts now replace lat/lon input  |
| Focused automated tests added           | ✅      | 77 focused tests passed in affected areas           |
| Full repository quality gate passed     | ✅      | `mvn spotless:apply verify` BUILD SUCCESS           |

### Key Decisions

| Decision                              | Rationale                                                         |
|---------------------------------------|-------------------------------------------------------------------|
| **Israel-first**                      | Target market focus, avoids over-engineering                      |
| **No external datasets**              | Start with 10-15 hardcoded cities, add complexity later if needed |
| **ZIP fallback**                      | Precise location for users outside major cities                   |
| **Coordinates hidden**                | Implementation detail, never exposed to users                     |
| **Country selector (Israel default)** | Foundation for future international expansion                     |

---

## Problem Statement

### Current State (Broken)

```
Profile Setup → "Enter Latitude: 32.0853" → Users confused → Skip location → Distance matching broken
```

**Issues:**
- ❌ Users don't know their lat/lon coordinates
- ❌ No validation feedback until save attempt
- ❌ High abandonment rate on location step
- ❌ Distance-based matching untestable in production

### Target State (Fixed)

```
Profile Setup → Select Country (Israel) → Search City (Tel Aviv) → [Optional: ZIP for precision] → Done
```

**Benefits:**
- ✅ Intuitive mental model (people know their city)
- ✅ One-click for major cities
- ✅ Fallback for edge cases
- ✅ Testable distance matching with real data

---

## Architecture Overview

### Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        UI LAYER (JavaFX)                        │
├─────────────────────────────────────────────────────────────────┤
│  ProfileController                                              │
│    └── LocationDialogController (NEW)                           │
│          ├── CountrySelector                                    │
│          ├── CitySearchBox (autocomplete)                       │
│          └── ZipCodeFallback                                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     VIEWMODEL LAYER                             │
├─────────────────────────────────────────────────────────────────┤
│  ProfileViewModel                                               │
│    └── LocationSelectionViewModel (NEW)                         │
│          ├── selectedCountryProperty                            │
│          ├── selectedCityProperty                               │
│          ├── zipCodeProperty                                    │
│          └── resolveCoordinates() → (lat, lon)                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      DOMAIN LAYER                               │
├─────────────────────────────────────────────────────────────────┤
│  LocationService (NEW)                                          │
│    ├── getCityCoordinates(cityName) → Optional<Coordinates>     │
│    ├── getZipCoordinates(zip, country) → Optional<Coordinates>  │
│    └── validateZipFormat(zip, country) → boolean                │
│                                                                 │
│  LocationModels (NEW)                                           │
│    ├── record Country(code, name, default)                      │
│    ├── record City(name, district, lat, lon, country)           │
│    ├── record ZipRange(prefix, country, latCenter, lonCenter)   │
│    └── record Coordinates(lat, lon, precision)                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      STORAGE LAYER                              │
├─────────────────────────────────────────────────────────────────┤
│  LocationDataRepository (NEW)                                   │
│    ├── loadCities(country) → List<City>                         │
│    └── loadZipRanges(country) → List<ZipRange>                  │
│                                                                 │
│  HardcodedDataProvider (NEW)                                    │
│    └── Inline city/ZIP data (no external files initially)       │
└─────────────────────────────────────────────────────────────────┘
```

### Data Flow

```
User Action          → Controller      → ViewModel           → Service         → Result
─────────────────────────────────────────────────────────────────────────────────────
Select Country       → onCountryPick() → setSelectedCountry() → ─               → Update UI
Type City Name       → onCitySearch()  → searchCities()       → getCityCoords() → Show suggestions
Click City           → onCitySelect()  → setSelectedCity()    → resolveCoords() → Save to User
Enter ZIP            → onZipSubmit()   → setZipCode()         → getZipCoords()  → Save to User
Save Location        → onSave()        → applyLocation()      → user.setLatLon()→ Persist to DB
```

---

## Detailed Specifications

### SPEC-1: Country Selection

#### 1.1 Requirements

| ID    | Requirement                                              | Priority |
|-------|----------------------------------------------------------|----------|
| C-1.1 | Country dropdown with Israel as default selection        | MUST     |
| C-1.2 | Display country flag emoji + name (e.g., "🇮🇱 Israel")  | MUST     |
| C-1.3 | "Coming Soon" placeholder for non-Israel countries       | MUST     |
| C-1.4 | Disable selection of "Coming Soon" countries             | MUST     |
| C-1.5 | Support keyboard navigation (arrow keys, type-to-select) | SHOULD   |

#### 1.2 Data Model

```java
// core/model/Country.java
public record Country(
    String code,           // ISO 3166-1 alpha-2: "IL", "US", "GB"
    String name,           // Display name: "Israel", "United States"
    String flagEmoji,      // Emoji: "🇮🇱", "🇺🇸", "🇬🇧"
    boolean available,     // true = selectable, false = "Coming Soon"
    boolean isDefault      // true = default selection on dialog open
) {}
```

#### 1.3 Initial Country List

```java
// Hardcoded in LocationDataRepository.java
List.of(
    new Country("IL", "Israel", "🇮🇱", true, true),   // Default
    new Country("US", "United States", "🇺🇸", false, false),
    new Country("GB", "United Kingdom", "🇬🇧", false, false),
    new Country("CA", "Canada", "🇨🇦", false, false),
    new Country("AU", "Australia", "🇦🇺", false, false)
)
```

#### 1.4 UI Specification

```
┌─────────────────────────────────────┐
│ Select Country                      │
├─────────────────────────────────────┤
│ ▼ 🇮🇱 Israel                       │  ← Selected (default)
│ ─────────────────────────────────── │
│   🇺🇸 United States (Coming Soon)  │  ← Disabled, grayed out
│   🇬🇧 United Kingdom (Coming Soon) │
│   🇨🇦 Canada (Coming Soon)         │
│   🇦🇺 Australia (Coming Soon)      │
└─────────────────────────────────────┘
```

**Interaction:**
- Click dropdown → shows list
- Click "Coming Soon" item → toast: "Available in future update"
- Click available country → select and close dropdown

---

### SPEC-2: City Search

#### 2.1 Requirements

| ID     | Requirement                                              | Priority |
|--------|----------------------------------------------------------|----------|
| CT-2.1 | Text input with autocomplete dropdown                    | MUST     |
| CT-2.2 | Fuzzy search (partial match, case-insensitive)           | MUST     |
| CT-2.3 | Display city name + district (if applicable)             | SHOULD   |
| CT-2.4 | Show "No results" state when search yields nothing       | MUST     |
| CT-2.5 | Support keyboard navigation (↑↓ arrows, Enter to select) | SHOULD   |
| CT-2.6 | Clear button (X) to reset selection                      | SHOULD   |

#### 2.2 Data Model

```java
// core/model/City.java
public record City(
    String name,           // City name: "Tel Aviv", "Jerusalem"
    String district,       // District/region: "Tel Aviv District", "Jerusalem District"
    double latitude,       // Center point latitude
    double longitude,      // Center point longitude
    String country,        // Country code: "IL"
    int priority           // Display order (1 = major city, shown first)
) {}
```

#### 2.3 Initial City Dataset (Israel)

```java
// Hardcoded in LocationDataRepository.java
// Priority: 1 = Major city (top of list), 2 = Secondary, 3 = Other
List.of(
    // Priority 1 - Major cities (show first in search)
    new City("Tel Aviv", "Tel Aviv District", 32.0853, 34.7818, "IL", 1),
    new City("Jerusalem", "Jerusalem District", 31.7683, 35.2137, "IL", 1),
    new City("Haifa", "Haifa District", 32.7940, 34.9896, "IL", 1),
    new City("Rishon LeZion", "Central District", 31.9642, 34.8054, "IL", 1),
    new City("Petah Tikva", "Central District", 32.0870, 34.8877, "IL", 1),

    // Priority 2 - Secondary cities
    new City("Ashdod", "Southern District", 31.8044, 34.6553, "IL", 2),
    new City("Netanya", "Central District", 32.3215, 34.8532, "IL", 2),
    new City("Be'er Sheva", "Southern District", 31.2518, 34.7913, "IL", 2),
    new City("Holon", "Tel Aviv District", 32.0117, 34.7738, "IL", 2),
    new City("Bnei Brak", "Tel Aviv District", 32.0808, 34.8338, "IL", 2),

    // Priority 3 - Other notable locations
    new City("Ramat Gan", "Tel Aviv District", 32.0703, 34.8267, "IL", 3),
    new City("Rehovot", "Central District", 31.8934, 34.8100, "IL", 3),
    new City("Herzliya", "Tel Aviv District", 32.1667, 34.8500, "IL", 3),
    new City("Kfar Saba", "Central District", 32.1742, 34.9067, "IL", 3),
    new City("Modi'in", "Central District", 31.8969, 35.0061, "IL", 3)
)
```

**Design Notes:**
- 15 cities covers ~60% of Israel's population
- Coordinates are city center (municipal building or central point)
- Easy to extend: add more cities to the list later

#### 2.4 Search Algorithm

```java
/**
 * Fuzzy search: matches if query is substring of name OR district
 * Case-insensitive, Hebrew-friendly (supports both scripts)
 * Results sorted by: priority ASC, then name ASC
 */
List<City> searchCities(String query, String countryCode) {
    String normalized = query.toLowerCase().trim();
    return cities.stream()
        .filter(c -> c.country().equals(countryCode))
        .filter(c -> c.name().toLowerCase().contains(normalized) ||
                     c.district().toLowerCase().contains(normalized))
        .sorted(Comparator.comparing(City::priority)
                          .thenComparing(City::name))
        .toList();
}
```

#### 2.5 UI Specification

```
┌─────────────────────────────────────────┐
│ Search City                             │
├─────────────────────────────────────────┤
│ [Tel Aviv                    ] [X]      │  ← Text input with clear button
│ ─────────────────────────────────────── │
│ 📍 Tel Aviv, Tel Aviv District          │  ← Suggestion 1 (highlighted)
│ 📍 Tel Aviv District (region)           │  ← Suggestion 2
│                                         │
│ [Cancel]              [Select City]     │  ← Disabled until city selected
└─────────────────────────────────────────┘

Empty state (no search yet):
┌─────────────────────────────────────────┐
│ Search City                             │
├─────────────────────────────────────────┤
│ [                              ]        │
│ ─────────────────────────────────────── │
│ Popular Cities:                         │
│   📍 Tel Aviv                           │
│   📍 Jerusalem                          │
│   📍 Haifa                              │
│   📍 Rishon LeZion                      │
│   📍 Petah Tikva                        │
└─────────────────────────────────────────┘

No results state:
┌─────────────────────────────────────────┐
│ Search City                             │
├─────────────────────────────────────────┤
│ [XYZ                           ] [X]    │
│ ─────────────────────────────────────── │
│                                         │
│      No cities found for "XYZ"          │
│      Try entering ZIP code instead      │
│                                         │
│            [Use ZIP Code]               │
└─────────────────────────────────────────┘
```

---

### SPEC-3: ZIP Code Fallback

#### 3.1 Requirements

| ID    | Requirement                                                   | Priority |
|-------|---------------------------------------------------------------|----------|
| Z-3.1 | Show ZIP input when city search fails or user chooses         | MUST     |
| Z-3.2 | Validate ZIP format per country (Israel: 7 digits)            | MUST     |
| Z-3.3 | Lookup coordinates from ZIP prefix (first 4 digits)           | MUST     |
| Z-3.4 | Show human-readable location name for ZIP                     | SHOULD   |
| Z-3.5 | Handle unknown ZIP gracefully (manual lat/lon as last resort) | SHOULD   |

#### 3.2 Data Model

```java
// core/model/ZipRange.java
public record ZipRange(
    String prefix,         // First 4 digits: "6701", "9100"
    String country,        // Country code: "IL"
    String city,           // Primary city: "Tel Aviv", "Jerusalem"
    String district,       // District: "Tel Aviv District"
    double latitude,       // Center point of ZIP area
    double longitude       // Center point of ZIP area
) {}
```

#### 3.3 Initial ZIP Dataset (Israel)

```java
// Hardcoded in LocationDataRepository.java
// Israel ZIP system: 7 digits, first 4 = geographic area
// Sample of major ZIP ranges (expand as needed)
List.of(
    // Tel Aviv area (67xx, 68xx)
    new ZipRange("6701", "IL", "Tel Aviv", "Tel Aviv District", 32.0650, 34.7700),
    new ZipRange("6702", "IL", "Tel Aviv", "Tel Aviv District", 32.0680, 34.7750),
    new ZipRange("6703", "IL", "Tel Aviv", "Tel Aviv District", 32.0700, 34.7800),
    new ZipRange("6704", "IL", "Tel Aviv", "Tel Aviv District", 32.0720, 34.7850),
    new ZipRange("6705", "IL", "Tel Aviv", "Tel Aviv District", 32.0750, 34.7900),
    new ZipRange("6706", "IL", "Tel Aviv", "Tel Aviv District", 32.0780, 34.7950),
    new ZipRange("6707", "IL", "Tel Aviv", "Tel Aviv District", 32.0800, 34.8000),
    new ZipRange("6708", "IL", "Tel Aviv", "Tel Aviv District", 32.0820, 34.8050),
    new ZipRange("6709", "IL", "Tel Aviv", "Tel Aviv District", 32.0850, 34.8100),
    new ZipRange("6710", "IL", "Tel Aviv", "Tel Aviv District", 32.0880, 34.8150),

    // Jerusalem area (91xx, 93xx)
    new ZipRange("9100", "IL", "Jerusalem", "Jerusalem District", 31.7683, 35.2137),
    new ZipRange("9101", "IL", "Jerusalem", "Jerusalem District", 31.7700, 35.2150),
    new ZipRange("9102", "IL", "Jerusalem", "Jerusalem District", 31.7720, 35.2170),
    new ZipRange("9103", "IL", "Jerusalem", "Jerusalem District", 31.7750, 35.2200),
    new ZipRange("9104", "IL", "Jerusalem", "Jerusalem District", 31.7780, 35.2230),

    // Haifa area (31xx, 33xx)
    new ZipRange("3100", "IL", "Haifa", "Haifa District", 32.7940, 34.9896),
    new ZipRange("3101", "IL", "Haifa", "Haifa District", 32.7960, 34.9910),
    new ZipRange("3102", "IL", "Haifa", "Haifa District", 32.7980, 34.9930),

    // Rishon LeZion (751x, 752x)
    new ZipRange("7510", "IL", "Rishon LeZion", "Central District", 31.9642, 34.8054),
    new ZipRange("7511", "IL", "Rishon LeZion", "Central District", 31.9660, 34.8070),

    // Petah Tikva (491x, 492x)
    new ZipRange("4910", "IL", "Petah Tikva", "Central District", 32.0870, 34.8877),
    new ZipRange("4911", "IL", "Petah Tikva", "Central District", 32.0890, 34.8890)
)
```

**Design Notes:**
- Israel ZIP: 7 digits, first 4 = geographic area (street/neighborhood level)
- ~20 ZIP ranges covers major population centers
- Coordinates = center of ZIP area (more precise than city center)
- Expand dataset incrementally based on user data

#### 3.4 ZIP Validation Rules

```java
// core/profile/ValidationService.java (extend existing)
public record ZipValidationResult(
    boolean valid,
    String errorMessage,
    String normalizedZip,    // "6701101" → "6701" (prefix for lookup)
    String locationName      // "Tel Aviv, Tel Aviv District"
) {}

public ZipValidationResult validateZip(String zip, String countryCode) {
    if (zip == null || zip.isBlank()) {
        return new ZipValidationResult(false, "ZIP code is required", null, null);
    }

    String normalized = zip.replaceAll("[\\s-]", ""); // Remove spaces/dashes

    switch (countryCode) {
        case "IL" -> {
            // Israel: 7 digits (e.g., "6701101")
            if (!normalized.matches("\\d{7}")) {
                return new ZipValidationResult(
                    false,
                    "Israeli ZIP code must be 7 digits (e.g., 6701101)",
                    null,
                    null
                );
            }
            String prefix = normalized.substring(0, 4); // "6701"
            // Lookup location name from ZIP range
            String locationName = lookupLocationName(prefix, "IL");
            return new ZipValidationResult(true, null, prefix, locationName);
        }

        default -> {
            return new ZipValidationResult(
                false,
                "ZIP validation not available for this country",
                null,
                null
            );
        }
    }
}
```

#### 3.5 UI Specification

```
┌─────────────────────────────────────────┐
│ Enter ZIP Code                          │
├─────────────────────────────────────────┤
│ Country: 🇮🇱 Israel                     │
│                                         │
│ ZIP Code: [6701101            ]         │  ← 7 digits for Israel
│                                         │
│ ℹ️ Israeli ZIP codes are 7 digits       │
│    (found on mail, bills, etc.)         │
│                                         │
│ ─────────────────────────────────────── │
│ Preview:                                │
│ 📍 Tel Aviv, Tel Aviv District          │  ← Shows after valid ZIP entered
│    (ZIP area: 6701)                     │
│                                         │
│ [Back to City Search]    [Use ZIP]      │  ← "Use ZIP" disabled until valid
└─────────────────────────────────────────┘

Invalid ZIP state:
┌─────────────────────────────────────────┐
│ Enter ZIP Code                          │
├─────────────────────────────────────────┤
│ Country: 🇮🇱 Israel                     │
│                                         │
│ ZIP Code: [12345               ]        │  ← Red border
│                                         │
│ ⚠️ Israeli ZIP code must be 7 digits    │  ← Error message
│    (e.g., 6701101)                      │
│                                         │
│ [Back to City Search]    [Use ZIP]      │  ← Disabled
└─────────────────────────────────────────┘

Unknown ZIP state (valid format, not in database):
┌─────────────────────────────────────────┐
│ Enter ZIP Code                          │
├─────────────────────────────────────────┤
│ Country: 🇮🇱 Israel                     │
│                                         │
│ ZIP Code: [9999999             ]        │
│                                         │
│ ⚠️ ZIP code not found in our database   │
│                                         │
│ Would you like to:                      │
│   ○ Use approximate coordinates         │  ← Default (center of Israel)
│   ○ Enter coordinates manually          │
│                                         │
│ [Back]                 [Continue]       │
└─────────────────────────────────────────┘
```

---

### SPEC-4: Location Selection Dialog (Unified UI)

#### 4.1 Requirements

| ID    | Requirement                                           | Priority |
|-------|-------------------------------------------------------|----------|
| D-4.1 | Single dialog with 3-step flow (Country → City → ZIP) | MUST     |
| D-4.2 | Progress indicator showing current step               | SHOULD   |
| D-4.3 | Back/Next navigation between steps                    | MUST     |
| D-4.4 | Cancel button closes without saving                   | MUST     |
| D-4.5 | Save button enabled only when valid location selected | MUST     |
| D-4.6 | Show current location (if already set) as context     | SHOULD   |

#### 4.2 Dialog Flow

```
┌─────────────────────────────────────────────────────────────┐
│ Step 1: Select Country                                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   [Country dropdown - SPEC-1]                               │
│                                                             │
│                          [Cancel] [Next]                    │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼ (Next, Israel selected)
┌─────────────────────────────────────────────────────────────┐
│ Step 2: Select City                              2 of 3     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   [City search - SPEC-2]                                    │
│                                                             │
│   ─────────────────────────────────────────────────         │
│   Can't find your city?                                     │
│   [Use ZIP Code Instead]                                    │
│                                                             │
│   [Back]                    [Cancel] [Save Location]        │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼ (No results / "Use ZIP Code Instead")
┌─────────────────────────────────────────────────────────────┐
│ Step 3: Enter ZIP Code                           3 of 3     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   [ZIP input - SPEC-3]                                      │
│                                                             │
│   [Back to City Search]     [Cancel] [Save Location]        │
└─────────────────────────────────────────────────────────────┘
```

#### 4.3 Dialog Dimensions

```java
// Flet-style dialog sizing (adapted for JavaFX)
DIALOG_WIDTH = 500
DIALOG_MIN_HEIGHT = 400
DIALOG_MAX_HEIGHT = 600
DIALOG_PADDING = 24
```

#### 4.4 Themed Components

```css
/* location-dialog.css - new file */
.location-dialog {
    -fx-background-color: -fx-background;
    -fx-background-radius: 12;
}

.location-dialog-header {
    -fx-font-size: 18px;
    -fx-font-weight: bold;
    -fx-text-fill: -fx-text-primary;
}

.location-dialog-subheader {
    -fx-font-size: 14px;
    -fx-text-fill: -fx-text-secondary;
}

.country-dropdown {
    -fx-background-radius: 8;
    -fx-border-radius: 8;
    -fx-padding: 8 12;
}

.country-dropdown-item {
    -fx-padding: 8 12;
}

.country-dropdown-item:disabled {
    -fx-opacity: 0.5;
    -fx-cursor: default;
}

.city-search-field {
    -fx-background-radius: 8;
    -fx-border-radius: 8;
    -fx-padding: 10 12;
}

.city-suggestion-item {
    -fx-padding: 10 12;
    -fx-cursor: hand;
}

.city-suggestion-item:hover {
    -fx-background-color: -fx-accent-light;
}

.city-suggestion-item:selected {
    -fx-background-color: -fx-accent;
    -fx-text-fill: -fx-text-on-accent;
}

.zip-field {
    -fx-background-radius: 8;
    -fx-border-radius: 8;
    -fx-padding: 10 12;
}

.zip-field:error {
    -fx-border-color: -fx-error;
    -fx-border-width: 2;
}

.location-preview {
    -fx-background-color: -fx-background-secondary;
    -fx-background-radius: 8;
    -fx-padding: 12;
}

.progress-indicator {
    -fx-font-size: 12px;
    -fx-text-fill: -fx-text-secondary;
}
```

---

### SPEC-5: ViewModel Integration

#### 5.1 New ViewModel

```java
// ui/viewmodel/LocationSelectionViewModel.java
package datingapp.ui.viewmodel;

import datingapp.core.model.User;
import datingapp.core.profile.LocationService;
import datingapp.core.model.LocationModels.City;
import datingapp.core.model.LocationModels.Country;
import datingapp.core.model.LocationModels.Coordinates;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.Optional;

public class LocationSelectionViewModel {
    // State
    private final ObjectProperty<Country> selectedCountry = new SimpleObjectProperty<>();
    private final ObjectProperty<City> selectedCity = new SimpleObjectProperty<>();
    private final StringProperty zipCode = new SimpleStringProperty("");
    private final StringProperty searchQuery = new SimpleStringProperty("");

    // UI State
    private final IntegerProperty currentStep = new SimpleIntegerProperty(1);
    private final BooleanProperty saveEnabled = new SimpleBooleanProperty(false);
    private final StringProperty errorMessage = new SimpleStringProperty("");

    // Data
    private final ObservableList<Country> countries = FXCollections.observableArrayList();
    private final ObservableList<City> citySuggestions = FXCollections.observableArrayList();

    // Services
    private final LocationService locationService;

    // Constructor
    public LocationSelectionViewModel(LocationService locationService) {
        this.locationService = locationService;
        this.countries.setAll(locationService.getAvailableCountries());

        // Set default country
        this.selectedCountry.set(
            countries.stream()
                .filter(Country::isDefault)
                .findFirst()
                .orElse(countries.get(0))
        );

        // Bind save enabled to valid state
        saveEnabled.bind(
            selectedCity.isNotNull()
                .or(zipCode.isNotEmpty().and(errorMessage.isEmpty()))
        );
    }

    // Properties
    public ObjectProperty<Country> selectedCountryProperty() { return selectedCountry; }
    public ObjectProperty<City> selectedCityProperty() { return selectedCity; }
    public StringProperty zipCodeProperty() { return zipCode; }
    public StringProperty searchQueryProperty() { return searchQuery; }
    public IntegerProperty currentStepProperty() { return currentStep; }
    public BooleanProperty saveEnabledProperty() { return saveEnabled; }
    public StringProperty errorMessageProperty() { return errorMessage; }
    public ObservableList<Country> countriesProperty() { return countries; }
    public ObservableList<City> citySuggestionsProperty() { return citySuggestions; }

    // Actions
    public void selectCountry(Country country) {
        if (!country.available()) {
            errorMessage.set("This country is not available yet. Coming soon!");
            return;
        }
        selectedCountry.set(country);
        selectedCity.set(null);
        zipCode.set("");
        searchQuery.set("");
        citySuggestions.clear();
        errorMessage.set("");
    }

    public void searchCities(String query) {
        searchQuery.set(query);
        if (query.isBlank()) {
            citySuggestions.clear();
            return;
        }

        List<City> results = locationService.searchCities(
            query,
            selectedCountry.get().code()
        );
        citySuggestions.setAll(results);
    }

    public void selectCity(City city) {
        selectedCity.set(city);
        zipCode.set("");
        errorMessage.set("");
        currentStep.set(2); // Move to step 2
    }

    public void setZipCode(String zip) {
        zipCode.set(zip);
        validateZip(zip);
    }

    private void validateZip(String zip) {
        var result = locationService.validateZip(
            zip,
            selectedCountry.get().code()
        );

        if (!result.valid()) {
            errorMessage.set(result.errorMessage());
            saveEnabled.set(false);
        } else {
            errorMessage.set("");
        }
    }

    public void goToPreviousStep() {
        if (currentStep.get() == 3) {
            currentStep.set(2);
        }
    }

    public void goToNextStep() {
        if (currentStep.get() == 1) {
            currentStep.set(2);
        }
    }

    public void goToZipStep() {
        currentStep.set(3);
    }

    public Optional<Coordinates> resolveCoordinates() {
        if (selectedCity.get() != null) {
            City city = selectedCity.get();
            return Optional.of(new Coordinates(
                city.latitude(),
                city.longitude(),
                "city",
                city.name() + ", " + city.district()
            ));
        }

        if (!zipCode.get().isBlank()) {
            var result = locationService.validateZip(
                zipCode.get(),
                selectedCountry.get().code()
            );
            if (result.valid()) {
                return locationService.getCoordinatesFromZip(
                    result.normalizedZip(),
                    selectedCountry.get().code()
                );
            }
        }

        return Optional.empty();
    }

    public void applyToUser(User user) {
        resolveCoordinates().ifPresent(coords -> {
            user.setLocation(coords.latitude(), coords.longitude());
        });
    }

    public void reset() {
        selectedCountry.set(countries.stream()
            .filter(Country::isDefault)
            .findFirst()
            .orElse(countries.get(0)));
        selectedCity.set(null);
        zipCode.set("");
        searchQuery.set("");
        citySuggestions.clear();
        errorMessage.set("");
        currentStep.set(1);
    }
}
```

#### 5.2 ProfileViewModel Integration

```java
// ui/viewmodel/ProfileViewModel.java (additions)

// Add field
private LocationSelectionViewModel locationViewModel;

// In constructor or initialize method
this.locationViewModel = new LocationSelectionViewModel(
    services.getLocationService()
);

// Add method to open location dialog
public void openLocationDialog() {
    // Pre-populate with current location if set
    if (currentUser != null && currentUser.hasLocation()) {
        // Find matching city/ZIP from existing coordinates
        var match = locationService.findLocationFromCoordinates(
            currentUser.getLat(),
            currentUser.getLon()
        );
        match.ifPresent(loc -> {
            loc.city().ifPresent(locationViewModel::selectCity);
            loc.zip().ifPresent(locationViewModel::setZipCode);
        });
    }

    // Show dialog (controller handles UI)
    // Controller calls: locationViewModel.resolveCoordinates() on save
}

// Update existing setLocationCoordinates to use new flow
public void setLocationCoordinates(double latitude, double longitude) {
    // Keep for backward compatibility, but prefer locationViewModel.applyToUser()
    this.latitude.set(latitude);
    this.longitude.set(longitude);
    hasLocation.set(true);
    updateLocationDisplay();
}
```

---

### SPEC-6: Service Layer

#### 6.1 LocationService Interface

```java
// core/profile/LocationService.java
package datingapp.core.profile;

import datingapp.core.model.LocationModels.*;
import java.util.List;
import java.util.Optional;

public interface LocationService {

    /**
     * Get all available countries for selection
     */
    List<Country> getAvailableCountries();

    /**
     * Search cities by name within a country
     * @param query Search query (partial match, case-insensitive)
     * @param countryCode ISO 3166-1 alpha-2 country code
     * @return Matching cities sorted by priority then name
     */
    List<City> searchCities(String query, String countryCode);

    /**
     * Validate ZIP code format and lookup location
     * @param zip ZIP code (may include spaces/dashes)
     * @param countryCode ISO 3166-1 alpha-2 country code
     * @return Validation result with normalized ZIP and location name
     */
    ZipValidationResult validateZip(String zip, String countryCode);

    /**
     * Get coordinates from validated ZIP prefix
     * @param zipPrefix First 4 digits of ZIP
     * @param countryCode ISO 3166-1 alpha-2 country code
     * @return Coordinates if ZIP found in database
     */
    Optional<Coordinates> getCoordinatesFromZip(String zipPrefix, String countryCode);

    /**
     * Find location from coordinates (reverse geocoding)
     * @param latitude Latitude
     * @param longitude Longitude
     * @return Matching city and/or ZIP if found
     */
    Optional<LocationMatch> findLocationFromCoordinates(double latitude, double longitude);
}

// Supporting records
record ZipValidationResult(
    boolean valid,
    String errorMessage,
    String normalizedZip,
    String locationName
) {}

record LocationMatch(
    Optional<City> city,
    Optional<String> zip,
    double distanceFromPoint // in km
) {}
```

#### 6.2 LocationService Implementation

```java
// core/profile/LocationServiceImpl.java
package datingapp.core.profile;

import datingapp.core.model.LocationModels.*;
import datingapp.core.storage.LocationDataRepository;
import java.util.List;
import java.util.Optional;

public class LocationServiceImpl implements LocationService {

    private final LocationDataRepository repository;

    public LocationServiceImpl(LocationDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Country> getAvailableCountries() {
        return repository.loadCountries();
    }

    @Override
    public List<City> searchCities(String query, String countryCode) {
        List<City> allCities = repository.loadCities(countryCode);
        String normalized = query.toLowerCase().trim();

        return allCities.stream()
            .filter(c -> c.name().toLowerCase().contains(normalized) ||
                        c.district().toLowerCase().contains(normalized))
            .sorted(Comparator.comparing(City::priority)
                              .thenComparing(City::name))
            .toList();
    }

    @Override
    public ZipValidationResult validateZip(String zip, String countryCode) {
        if (zip == null || zip.isBlank()) {
            return new ZipValidationResult(false, "ZIP code is required", null, null);
        }

        String normalized = zip.replaceAll("[\\s-]", "");

        return switch (countryCode) {
            case "IL" -> validateIsraelZip(normalized);
            default -> new ZipValidationResult(
                false,
                "ZIP validation not available for this country",
                null,
                null
            );
        };
    }

    private ZipValidationResult validateIsraelZip(String zip) {
        if (!zip.matches("\\d{7}")) {
            return new ZipValidationResult(
                false,
                "Israeli ZIP code must be 7 digits (e.g., 6701101)",
                null,
                null
            );
        }

        String prefix = zip.substring(0, 4);
        List<ZipRange> zipRanges = repository.loadZipRanges("IL");

        Optional<ZipRange> match = zipRanges.stream()
            .filter(z -> z.prefix().equals(prefix))
            .findFirst();

        if (match.isPresent()) {
            ZipRange range = match.get();
            String locationName = range.city() + ", " + range.district();
            return new ZipValidationResult(true, null, prefix, locationName);
        } else {
            // Valid format but not in database
            return new ZipValidationResult(true, null, prefix, null);
        }
    }

    @Override
    public Optional<Coordinates> getCoordinatesFromZip(String zipPrefix, String countryCode) {
        List<ZipRange> zipRanges = repository.loadZipRanges(countryCode);

        return zipRanges.stream()
            .filter(z -> z.prefix().equals(zipPrefix))
            .findFirst()
            .map(z -> new Coordinates(
                z.latitude(),
                z.longitude(),
                "zip",
                z.city() + ", " + z.district()
            ));
    }

    @Override
    public Optional<LocationMatch> findLocationFromCoordinates(double latitude, double longitude) {
        // Find nearest city
        List<City> cities = repository.loadCities("IL");
        Optional<City> nearestCity = cities.stream()
            .min(Comparator.comparingDouble(c ->
                distance(latitude, longitude, c.latitude(), c.longitude())
            ));

        // Find matching ZIP
        List<ZipRange> zipRanges = repository.loadZipRanges("IL");
        Optional<ZipRange> nearestZip = zipRanges.stream()
            .min(Comparator.comparingDouble(z ->
                distance(latitude, longitude, z.latitude(), z.longitude())
            ));

        if (nearestCity.isPresent() || nearestZip.isPresent()) {
            double cityDist = nearestCity
                .map(c -> distance(latitude, longitude, c.latitude(), c.longitude()))
                .orElse(Double.MAX_VALUE);
            double zipDist = nearestZip
                .map(z -> distance(latitude, longitude, z.latitude(), z.longitude()))
                .orElse(Double.MAX_VALUE);

            return Optional.of(new LocationMatch(
                nearestCity.filter(c -> cityDist < 5.0), // Within 5km
                nearestZip.map(z -> z.prefix()).filter(z -> zipDist < 2.0), // Within 2km
                Math.min(cityDist, zipDist)
            ));
        }

        return Optional.empty();
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula for distance in km
        double R = 6371; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }
}
```

#### 6.3 LocationDataRepository

```java
// core/storage/LocationDataRepository.java
package datingapp.core.storage;

import datingapp.core.model.LocationModels.*;
import java.util.List;

public interface LocationDataRepository {
    List<Country> loadCountries();
    List<City> loadCities(String countryCode);
    List<ZipRange> loadZipRanges(String countryCode);
}
```

#### 6.4 HardcodedDataProvider (Initial Implementation)

```java
// core/storage/HardcodedLocationDataProvider.java
package datingapp.core.storage;

import datingapp.core.model.LocationModels.*;
import java.util.List;

public class HardcodedLocationDataProvider implements LocationDataRepository {

    @Override
    public List<Country> loadCountries() {
        return List.of(
            new Country("IL", "Israel", "🇮🇱", true, true),
            new Country("US", "United States", "🇺🇸", false, false),
            new Country("GB", "United Kingdom", "🇬🇧", false, false),
            new Country("CA", "Canada", "🇨🇦", false, false),
            new Country("AU", "Australia", "🇦🇺", false, false)
        );
    }

    @Override
    public List<City> loadCities(String countryCode) {
        if (!"IL".equals(countryCode)) {
            return List.of();
        }

        return List.of(
            // Priority 1 - Major cities
            new City("Tel Aviv", "Tel Aviv District", 32.0853, 34.7818, "IL", 1),
            new City("Jerusalem", "Jerusalem District", 31.7683, 35.2137, "IL", 1),
            new City("Haifa", "Haifa District", 32.7940, 34.9896, "IL", 1),
            new City("Rishon LeZion", "Central District", 31.9642, 34.8054, "IL", 1),
            new City("Petah Tikva", "Central District", 32.0870, 34.8877, "IL", 1),

            // Priority 2 - Secondary cities
            new City("Ashdod", "Southern District", 31.8044, 34.6553, "IL", 2),
            new City("Netanya", "Central District", 32.3215, 34.8532, "IL", 2),
            new City("Be'er Sheva", "Southern District", 31.2518, 34.7913, "IL", 2),
            new City("Holon", "Tel Aviv District", 32.0117, 34.7738, "IL", 2),
            new City("Bnei Brak", "Tel Aviv District", 32.0808, 34.8338, "IL", 2),

            // Priority 3 - Other locations
            new City("Ramat Gan", "Tel Aviv District", 32.0703, 34.8267, "IL", 3),
            new City("Rehovot", "Central District", 31.8934, 34.8100, "IL", 3),
            new City("Herzliya", "Tel Aviv District", 32.1667, 34.8500, "IL", 3),
            new City("Kfar Saba", "Central District", 32.1742, 34.9067, "IL", 3),
            new City("Modi'in", "Central District", 31.8969, 35.0061, "IL", 3)
        );
    }

    @Override
    public List<ZipRange> loadZipRanges(String countryCode) {
        if (!"IL".equals(countryCode)) {
            return List.of();
        }

        return List.of(
            // Tel Aviv area
            new ZipRange("6701", "IL", "Tel Aviv", "Tel Aviv District", 32.0650, 34.7700),
            new ZipRange("6702", "IL", "Tel Aviv", "Tel Aviv District", 32.0680, 34.7750),
            new ZipRange("6703", "IL", "Tel Aviv", "Tel Aviv District", 32.0700, 34.7800),
            new ZipRange("6704", "IL", "Tel Aviv", "Tel Aviv District", 32.0720, 34.7850),
            new ZipRange("6705", "IL", "Tel Aviv", "Tel Aviv District", 32.0750, 34.7900),
            new ZipRange("6706", "IL", "Tel Aviv", "Tel Aviv District", 32.0780, 34.7950),
            new ZipRange("6707", "IL", "Tel Aviv", "Tel Aviv District", 32.0800, 34.8000),
            new ZipRange("6708", "IL", "Tel Aviv", "Tel Aviv District", 32.0820, 34.8050),
            new ZipRange("6709", "IL", "Tel Aviv", "Tel Aviv District", 32.0850, 34.8100),
            new ZipRange("6710", "IL", "Tel Aviv", "Tel Aviv District", 32.0880, 34.8150),

            // Jerusalem area
            new ZipRange("9100", "IL", "Jerusalem", "Jerusalem District", 31.7683, 35.2137),
            new ZipRange("9101", "IL", "Jerusalem", "Jerusalem District", 31.7700, 35.2150),
            new ZipRange("9102", "IL", "Jerusalem", "Jerusalem District", 31.7720, 35.2170),
            new ZipRange("9103", "IL", "Jerusalem", "Jerusalem District", 31.7750, 35.2200),
            new ZipRange("9104", "IL", "Jerusalem", "Jerusalem District", 31.7780, 35.2230),

            // Haifa area
            new ZipRange("3100", "IL", "Haifa", "Haifa District", 32.7940, 34.9896),
            new ZipRange("3101", "IL", "Haifa", "Haifa District", 32.7960, 34.9910),
            new ZipRange("3102", "IL", "Haifa", "Haifa District", 32.7980, 34.9930),

            // Rishon LeZion
            new ZipRange("7510", "IL", "Rishon LeZion", "Central District", 31.9642, 34.8054),
            new ZipRange("7511", "IL", "Rishon LeZion", "Central District", 31.9660, 34.8070),

            // Petah Tikva
            new ZipRange("4910", "IL", "Petah Tikva", "Central District", 32.0870, 34.8877),
            new ZipRange("4911", "IL", "Petah Tikva", "Central District", 32.0890, 34.8890)
        );
    }
}
```

---

### SPEC-7: Service Registry Wiring

#### 7.1 Update ServiceRegistry

```java
// core/ServiceRegistry.java (additions)

// Add field
private final LocationService locationService;

// Add getter
public LocationService getLocationService() {
    return locationService;
}

// In constructor
this.locationService = new LocationServiceImpl(
    new HardcodedLocationDataProvider()
);
```

---

### SPEC-8: ProfileController Integration

#### 8.1 LocationDialogController (New)

```java
// ui/screen/LocationDialogController.java
package datingapp.ui.screen;

import datingapp.ui.viewmodel.LocationSelectionViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.Optional;

public class LocationDialogController {

    @FXML private Label dialogTitle;
    @FXML private Label stepIndicator;
    @FXML private VBox step1Container; // Country selection
    @FXML private VBox step2Container; // City search
    @FXML private VBox step3Container; // ZIP input
    @FXML private ComboBox<Country> countryCombo;
    @FXML private TextField citySearchField;
    @FXML private ListView<City> citySuggestionsList;
    @FXML private TextField zipField;
    @FXML private Label zipPreviewLabel;
    @FXML private Label errorLabel;
    @FXML private Button backButton;
    @FXML private Button nextButton;
    @FXML private Button cancelButton;
    @FXML private Button saveButton;

    private LocationSelectionViewModel viewModel;
    private Stage dialogStage;

    public void initialize() {
        bindViewModel();
        setupEventHandlers();
        showStep(1);
    }

    public void setViewModel(LocationSelectionViewModel viewModel) {
        this.viewModel = viewModel;
        bindViewModel();
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    private void bindViewModel() {
        if (viewModel == null) return;

        countryCombo.setItems(viewModel.countriesProperty());
        countryCombo.getSelectionModel().select(viewModel.selectedCountryProperty().get());
        countryCombo.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> viewModel.selectCountry(newVal)
        );

        citySearchField.textProperty().bindBidirectional(viewModel.searchQueryProperty());
        citySuggestionsList.setItems(viewModel.citySuggestionsProperty());

        zipField.textProperty().bindBidirectional(viewModel.zipCodeProperty());
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        saveButton.disableProperty().bind(viewModel.saveEnabledProperty().not());

        // Update step indicator
        viewModel.currentStepProperty().addListener((obs, oldVal, newVal) -> {
            showStep(newVal.intValue());
        });
    }

    private void setupEventHandlers() {
        citySuggestionsList.setOnMouseClicked(event -> {
            City selected = citySuggestionsList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                viewModel.selectCity(selected);
            }
        });

        backButton.setOnAction(event -> {
            viewModel.goToPreviousStep();
        });

        nextButton.setOnAction(event -> {
            viewModel.goToNextStep();
        });

        cancelButton.setOnAction(event -> {
            dialogStage.close();
        });

        saveButton.setOnAction(event -> {
            viewModel.resolveCoordinates().ifPresent(coords -> {
                // Pass coordinates back to ProfileController
                ProfileController controller = getProfileController();
                if (controller != null) {
                    controller.onLocationSelected(coords);
                }
                dialogStage.close();
            });
        });
    }

    private void showStep(int step) {
        step1Container.setVisible(step == 1);
        step1Container.setManaged(step == 1);

        step2Container.setVisible(step == 2);
        step2Container.setManaged(step == 2);

        step3Container.setVisible(step == 3);
        step3Container.setManaged(step == 3);

        stepIndicator.setText("Step %d of 3".formatted(step));

        backButton.setDisable(step == 1);
        nextButton.setDisable(step != 1);
    }

    private ProfileController getProfileController() {
        // Get reference to parent ProfileController
        // Implementation depends on your navigation pattern
        return null; // TODO: Implement based on your architecture
    }
}
```

#### 8.2 ProfileController Updates

```java
// ui/screen/ProfileController.java (additions)

// Add field
@FXML private Button setLocationButton;

// In initialize method
setLocationButton.setOnAction(event -> {
    openLocationDialog();
});

private void openLocationDialog() {
    try {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/location_dialog.fxml")
        );
        loader.setControllerFactory(controllerFactory);

        Dialog<ButtonType> dialog = createThemedDialog(
            "Set Location",
            "Select your location for distance-based matching"
        );

        Parent root = loader.load();
        LocationDialogController dialogController = loader.getController();

        LocationSelectionViewModel locationViewModel =
            new LocationSelectionViewModel(services.getLocationService());
        dialogController.setViewModel(locationViewModel);
        dialogController.setDialogStage((Stage) dialog.getDialogPane().getScene().getWindow());

        dialog.getDialogPane().setContent(root);

        Optional<ButtonType> result = dialog.showAndWait();
        // Location applied in dialogController.saveButton handler
    } catch (IOException e) {
        logError("Failed to open location dialog", e);
        UiFeedbackService.showError("Unable to open location selector");
    }
}

public void onLocationSelected(Coordinates coords) {
    // Update ProfileViewModel with resolved coordinates
    profileViewModel.setLocationCoordinates(coords.latitude(), coords.longitude());

    // Save to user profile
    saveProfile();
}
```

---

### SPEC-9: FXML Files

#### 9.1 location_dialog.fxml

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>
<?import javafx.geometry.Insets?>

<VBox xmlns="http://javafx.com/javafx"
      xmlns:fx="http://javafx.com/fxml"
      fx:controller="datingapp.ui.screen.LocationDialogController"
      styleClass="location-dialog"
      spacing="16"
      padding="24">

    <!-- Header -->
    <VBox spacing="8">
        <Label fx:id="dialogTitle"
               text="Set Your Location"
               styleClass="location-dialog-header"/>
        <Label fx:id="stepIndicator"
               text="Step 1 of 3"
               styleClass="location-dialog-subheader"/>
    </VBox>

    <!-- Step 1: Country Selection -->
    <VBox fx:id="step1Container" spacing="12">
        <Label text="Select Country" styleClass="section-label"/>
        <ComboBox fx:id="countryCombo"
                  promptText="Choose country"
                  styleClass="country-dropdown"
                  maxWidth="Infinity"/>
        <Button fx:id="nextButton"
                text="Next"
                defaultButton="true"
                HBox.hgrow="ALWAYS"/>
    </VBox>

    <!-- Step 2: City Search -->
    <VBox fx:id="step2Container" spacing="12">
        <Label text="Search City" styleClass="section-label"/>
        <TextField fx:id="citySearchField"
                   promptText="Type city name (e.g., Tel Aviv)"
                   styleClass="city-search-field"/>
        <ListView fx:id="citySuggestionsList"
                  styleClass="city-suggestions-list"
                  VBox.vgrow="ALWAYS"
                  prefHeight="200"/>
        <Separator/>
        <Label text="Can't find your city?" styleClass="secondary-label"/>
        <Button text="Use ZIP Code Instead"
                onAction="#goToZipStep"
                styleClass="secondary-button"/>
        <HBox spacing="12">
            <Button fx:id="backButton" text="Back" HBox.hgrow="ALWAYS"/>
            <Button fx:id="cancelButton" text="Cancel" HBox.hgrow="ALWAYS"/>
            <Button fx:id="saveButton"
                    text="Save Location"
                    disable="true"
                    styleClass="primary-button"
                    HBox.hgrow="ALWAYS"/>
        </HBox>
    </VBox>

    <!-- Step 3: ZIP Input -->
    <VBox fx:id="step3Container" spacing="12">
        <Label text="Enter ZIP Code" styleClass="section-label"/>
        <HBox spacing="12" alignment="CENTER_LEFT">
            <Label text="Country:"/>
            <Label fx:id="selectedCountryLabel" text="🇮🇱 Israel"/>
        </HBox>
        <TextField fx:id="zipField"
                   promptText="7-digit ZIP code (e.g., 6701101)"
                   styleClass="zip-field"
                   maxWidth="200"/>
        <Label text="ℹ️ Israeli ZIP codes are 7 digits (found on mail, bills, etc.)"
               styleClass="helper-label"/>
        <Label fx:id="zipPreviewLabel"
               styleClass="location-preview"
               visible="false"
               managed="false"/>
        <Label fx:id="errorLabel"
               styleClass="error-label"
               visible="false"
               managed="false"/>
        <HBox spacing="12">
            <Button text="Back to City Search"
                    onAction="#goToPreviousStep"
                    HBox.hgrow="ALWAYS"/>
            <Button fx:id="cancelButton" text="Cancel" HBox.hgrow="ALWAYS"/>
            <Button fx:id="saveButton"
                    text="Save Location"
                    disable="true"
                    styleClass="primary-button"
                    HBox.hgrow="ALWAYS"/>
        </HBox>
    </VBox>

</VBox>
```

---

### SPEC-10: Database Schema (No Changes Required)

**Important:** No database schema changes needed. The `User` table already has `lat` and `lon` columns. This feature only changes **how users input** location data, not how it's stored.

```sql
-- Existing schema (unchanged)
CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    lat DOUBLE,
    lon DOUBLE,
    -- ... other fields
);
```

---

## Implementation Phases

### Phase 1: Foundation (Day 1)

| Task                                        | Files to Create/Modify                            | Estimated Time |
|---------------------------------------------|---------------------------------------------------|----------------|
| 1.1 Create LocationModels records           | `core/model/LocationModels.java`                  | 30 min         |
| 1.2 Create LocationService interface        | `core/profile/LocationService.java`               | 20 min         |
| 1.3 Create LocationDataRepository interface | `core/storage/LocationDataRepository.java`        | 15 min         |
| 1.4 Create HardcodedLocationDataProvider    | `core/storage/HardcodedLocationDataProvider.java` | 45 min         |
| 1.5 Create LocationServiceImpl              | `core/profile/LocationServiceImpl.java`           | 60 min         |
| 1.6 Wire into ServiceRegistry               | `core/ServiceRegistry.java`                       | 15 min         |
| 1.7 Add ZIP validation to ValidationService | `core/profile/ValidationService.java`             | 30 min         |

**Phase 1 Deliverable:** Backend services functional, testable via unit tests.

---

### Phase 2: ViewModel (Day 2 Morning)

| Task                                    | Files to Create/Modify                             | Estimated Time |
|-----------------------------------------|----------------------------------------------------|----------------|
| 2.1 Create LocationSelectionViewModel   | `ui/viewmodel/LocationSelectionViewModel.java`     | 90 min         |
| 2.2 Update ProfileViewModel integration | `ui/viewmodel/ProfileViewModel.java`               | 30 min         |
| 2.3 Write ViewModel unit tests          | `ui/viewmodel/LocationSelectionViewModelTest.java` | 60 min         |

**Phase 2 Deliverable:** ViewModel layer complete with tests.

---

### Phase 3: UI Implementation (Day 2 Afternoon + Day 3 Morning)

| Task                                                   | Files to Create/Modify                         | Estimated Time |
|--------------------------------------------------------|------------------------------------------------|----------------|
| 3.1 Create location_dialog.fxml                        | `src/main/resources/fxml/location_dialog.fxml` | 45 min         |
| 3.2 Create LocationDialogController                    | `ui/screen/LocationDialogController.java`      | 60 min         |
| 3.3 Create location-dialog.css                         | `src/main/resources/css/location-dialog.css`   | 30 min         |
| 3.4 Integrate into ProfileController                   | `ui/screen/ProfileController.java`             | 45 min         |
| 3.5 Update ProfileView FXML (setLocationButton wiring) | `src/main/resources/fxml/profile_view.fxml`    | 15 min         |

**Phase 3 Deliverable:** Complete UI flow, manually testable.

---

### Phase 4: Testing & Polish (Day 3 Afternoon)

| Task                                                  | Type             | Estimated Time |
|-------------------------------------------------------|------------------|----------------|
| 4.1 Unit tests: LocationServiceImpl                   | Unit Test        | 45 min         |
| 4.2 Unit tests: HardcodedLocationDataProvider         | Unit Test        | 30 min         |
| 4.3 Integration tests: LocationSelectionViewModel     | Integration Test | 45 min         |
| 4.4 Manual testing: All user flows                    | Manual QA        | 60 min         |
| 4.5 Bug fixes and edge cases                          | Bug Fix          | 60 min         |
| 4.6 Accessibility audit (keyboard nav, screen reader) | Accessibility    | 30 min         |
| 4.7 Performance check (search responsiveness)         | Performance      | 15 min         |

**Phase 4 Deliverable:** Production-ready feature with test coverage.

---

## Testing Strategy

### Unit Tests

```java
// test/java/datingapp/core/profile/LocationServiceImplTest.java
@Test
void searchCities_returnsMatchingCities_sortedByPriority() {
    List<City> results = locationService.searchCities("tel", "IL");

    assertEquals(5, results.size());
    assertEquals("Tel Aviv", results.get(0).name()); // Priority 1
    assertEquals("Tel Aviv District", results.get(1).district());
}

@Test
void validateIsraelZip_validFormat_returnsSuccess() {
    var result = locationService.validateZip("6701101", "IL");

    assertTrue(result.valid());
    assertEquals("6701", result.normalizedZip());
    assertEquals("Tel Aviv, Tel Aviv District", result.locationName());
}

@Test
void validateIsraelZip_invalidFormat_returnsError() {
    var result = locationService.validateZip("12345", "IL");

    assertFalse(result.valid());
    assertTrue(result.errorMessage().contains("7 digits"));
}
```

### Integration Tests

```java
// test/java/datingapp/ui/viewmodel/LocationSelectionViewModelTest.java
@Test
void selectCity_enablesSaveButton() {
    City telAviv = new City("Tel Aviv", "Tel Aviv District", 32.0853, 34.7818, "IL", 1);

    viewModel.selectCity(telAviv);

    assertTrue(viewModel.saveEnabledProperty().get());
}

@Test
void resolveCoordinates_returnsCityCoordinates() {
    City jerusalem = new City("Jerusalem", "Jerusalem District", 31.7683, 35.2137, "IL", 1);
    viewModel.selectCity(jerusalem);

    Optional<Coordinates> coords = viewModel.resolveCoordinates();

    assertTrue(coords.isPresent());
    assertEquals(31.7683, coords.get().latitude(), 0.0001);
    assertEquals(35.2137, coords.get().longitude(), 0.0001);
}
```

### Manual Test Cases

| Test Case                    | Steps                                                     | Expected Result                             |
|------------------------------|-----------------------------------------------------------|---------------------------------------------|
| TC-1: Select major city      | Country → Israel → Search "Tel" → Click "Tel Aviv" → Save | Location saved as 32.0853, 34.7818          |
| TC-2: Select via ZIP         | Country → Israel → "Use ZIP" → Enter "6701101" → Save     | Location saved as 32.0650, 34.7700          |
| TC-3: Invalid ZIP            | Enter "12345"                                             | Error: "Israeli ZIP code must be 7 digits"  |
| TC-4: Unknown ZIP            | Enter "9999999"                                           | Shows "ZIP not found" with fallback options |
| TC-5: Coming Soon country    | Click "United States"                                     | Toast: "Available in future update"         |
| TC-6: Keyboard navigation    | Tab through fields, arrow keys in dropdown                | Full keyboard accessibility                 |
| TC-7: Cancel flow            | Start flow → Click Cancel                                 | No changes saved                            |
| TC-8: Edit existing location | User with existing location → Open dialog                 | Pre-populated with nearest city/ZIP         |

---

## Rollback Plan

If feature causes issues in production:

### Immediate Rollback (< 5 min)

```java
// ProfileController.java - revert setLocationButton handler
setLocationButton.setOnAction(event -> {
    // TEMPORARY: Revert to old lat/lon dialog
    showLegacyLocationDialog();
});
```

### Full Rollback (< 30 min)

1. Revert git commit with location feature
2. Restore previous ProfileController
3. Remove new FXML/CSS files from resources
4. Deploy previous version

### Data Migration (Not Required)

**No data migration needed** — existing `lat`/`lon` values remain unchanged. Users can update location via new flow at their convenience.

---

## Future Enhancements (Post-MVP)

| Enhancement                | Priority | Effort | Description                                          |
|----------------------------|----------|--------|------------------------------------------------------|
| Expand city dataset        | Medium   | 2h     | Add 50-100 more Israeli cities                       |
| International ZIP support  | Low      | 8h     | Add ZIP ranges for US/UK/CA/AU                       |
| IP geolocation auto-detect | Medium   | 4h     | Pre-fill location on first visit                     |
| Map preview                | Low      | 12h    | Show selected location on static map                 |
| Neighborhood-level data    | Low      | 16h    | Tel Aviv neighborhoods (Florentin, Ramat Aviv, etc.) |
| Hebrew language support    | High     | 4h     | Hebrew city names, RTL UI                            |
| "Near me" radius selector  | Medium   | 3h     | User sets search radius (5km, 10km, 25km)            |

---

## Success Metrics

### Quantitative

| Metric                                  | Target       | Measurement                                                |
|-----------------------------------------|--------------|------------------------------------------------------------|
| Profile completion rate (location step) | > 85%        | Analytics: users who set location / users who reached step |
| Time to set location                    | < 30 seconds | Performance monitoring                                     |
| City search success rate                | > 90%        | Analytics: city selected / city searches                   |
| ZIP fallback usage                      | < 20%        | Analytics: ZIP saves / total location saves                |

### Qualitative

- ✅ No user support tickets about "not knowing coordinates"
- ✅ Distance-based matching testable with real user data
- ✅ Code review approval from team
- ✅ Accessibility audit passed (WCAG 2.1 AA)

---

## Risk Assessment

| Risk                           | Probability | Impact | Mitigation                                            |
|--------------------------------|-------------|--------|-------------------------------------------------------|
| City dataset too small         | Medium      | Low    | Start with 15 cities, add more based on user data     |
| ZIP database incomplete        | Medium      | Low    | Fallback to manual coordinates (hidden debug mode)    |
| UI too complex                 | Low         | Medium | User testing before full rollout                      |
| Performance issues (search)    | Low         | Low    | Limit suggestions to 10 items, debounce input         |
| International users frustrated | Medium      | Medium | Clear "Israel-first" messaging, roadmap for expansion |

---

## Dependencies

### Internal

- `ProfileController` — Integration point
- `ProfileViewModel` — State management
- `ValidationService` — ZIP validation
- `ServiceRegistry` — Service wiring

### External

- **None** — All data hardcoded initially

---

## Files Checklist

### New Files (13)

```
src/main/java/datingapp/
  core/
    model/LocationModels.java
    profile/
      LocationService.java
      LocationServiceImpl.java
    storage/
      LocationDataRepository.java
      HardcodedLocationDataProvider.java
  ui/
    viewmodel/LocationSelectionViewModel.java
    screen/
      LocationDialogController.java
      LocationDialogController.java (test)

src/main/resources/
  fxml/location_dialog.fxml
  css/location-dialog.css

src/test/java/datingapp/
  core/profile/LocationServiceImplTest.java
  core/storage/HardcodedLocationDataProviderTest.java
  ui/viewmodel/LocationSelectionViewModelTest.java
```

### Modified Files (6)

```
src/main/java/datingapp/
  core/ServiceRegistry.java
  core/profile/ValidationService.java
  ui/viewmodel/ProfileViewModel.java
  ui/screen/ProfileController.java

src/main/resources/
  fxml/profile_view.fxml (if setLocationButton needs wiring)
```

---

## Approval Checklist

Before implementation begins:

- [ ] Product owner reviewed and approved spec
- [ ] Technical lead approved architecture
- [ ] UX designer reviewed UI mockups
- [ ] QA team reviewed test plan
- [ ] Accessibility specialist reviewed accessibility requirements
- [ ] Security review completed (no PII exposure risks)
- [ ] Performance budget defined (< 100ms search response)

---

## Notes for Implementation Agent

### Coding Standards

1. Follow existing project conventions (checkstyle, PMD, Spotless)
2. Use nested enums from owner types (`User.Gender`, not standalone)
3. Use `AppClock.now()` for any time-based logic
4. Keep `core/` free from UI/framework imports
5. ViewModels use `ui/async` abstractions for async operations

### Testing Requirements

- Minimum 80% line coverage for new code
- All public methods tested
- Edge cases covered (empty input, invalid data, network failures)
- Integration tests for ViewModel + Service layer

### Documentation

- JavaDoc for all public APIs
- Inline comments for complex logic
- Update AGENTS.md with new layer overview
- Add changelog entry to QWEN.md

### Git Hygiene

- Atomic commits (one feature per commit)
- Descriptive commit messages
- Link to this plan in commit messages
- Branch name: `feature/location-input-v2`

---

## Appendix A: Coordinate Reference Table

### Israeli Cities Coordinates

| City          | Latitude | Longitude | Source             |
|---------------|----------|-----------|--------------------|
| Tel Aviv      | 32.0853  | 34.7818   | City Hall          |
| Jerusalem     | 31.7683  | 35.2137   | City Hall          |
| Haifa         | 32.7940  | 34.9896   | City Hall          |
| Rishon LeZion | 31.9642  | 34.8054   | City Center        |
| Petah Tikva   | 32.0870  | 34.8877   | City Hall          |
| Ashdod        | 31.8044  | 34.6553   | Port Area          |
| Netanya       | 32.3215  | 34.8532   | City Center        |
| Be'er Sheva   | 31.2518  | 34.7913   | City Hall          |
| Holon         | 32.0117  | 34.7738   | City Center        |
| Bnei Brak     | 32.0808  | 34.8338   | City Hall          |
| Ramat Gan     | 32.0703  | 34.8267   | City Center        |
| Rehovot       | 31.8934  | 34.8100   | Weizmann Institute |
| Herzliya      | 32.1667  | 34.8500   | Pituach Area       |
| Kfar Saba     | 32.1742  | 34.9067   | City Hall          |
| Modi'in       | 31.8969  | 35.0061   | City Center        |

### Israel ZIP Code Structure

```
Format: 7 digits (NNNNNNN)
Example: 6701101

Structure:
- First 2 digits: Geographic region (67 = Tel Aviv area)
- Next 2 digits: Neighborhood/street group (01 = Center)
- Last 3 digits: Specific building/street (101 = Building number)

For this feature, we use first 4 digits (6701) for area-level precision.
```

---

**END OF IMPLEMENTATION PLAN**
