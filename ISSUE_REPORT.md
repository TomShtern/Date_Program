# Comprehensive Issue Report - Dating App

**Date**: 2026-01-12
**Total Violations**: 213

---

## Summary by Category

| Category | Count | Severity |
|----------|-------|----------|
| Console Encoding (Windows) | 2 | **CRITICAL** |
| Missing Braces | 113 | **HIGH** |
| Missing Javadoc | 46 | **HIGH** |
| Indentation | 25 | MEDIUM |
| Variable Naming | 18 | MEDIUM |
| Variable Declaration Distance | 13 | MEDIUM |
| Line Length | 10 | LOW |
| Javadoc Formatting | 11 | LOW |
| Unicode Escapes | 6 | LOW |
| Switch Default | 1 | LOW |
| Multiple Declarations | 1 | LOW |
| Maven Configuration | 1 | LOW |

---

## CATEGORY 1: CRITICAL - User Experience (2)

### 1.1 Console Encoding - Emojis Display as Garbage
- **Impact**: ALL emojis show as `≡ƒל╣` instead of 🌹 throughout the application
- **Cause**: Windows console defaults to CP437/CP850, not UTF-8
- **Fix Required**:
  - Set console to UTF-8: `chcp 65001`
  - Document in README.md
  - Consider removing emojis or providing plain-text fallback

### 1.2 Missing Windows Setup Instructions
- **Location**: README.md
- **Fix Required**: Add Windows-specific setup section

---

## CATEGORY 2: HIGH PRIORITY - Missing Braces (113)

Missing braces on single-line if statements creates maintenance risk.

### CLI Package (48):
- **ProfileHandler.java**: 42 violations
  - Lines: 175, 198, 205, 212, 269, 360, 371, 383, 396
  - Lines: 410, 412, 414, 416, 418, 420
  - Lines: 602, 604, 606, 608, 609, 613, 615, 617, 619, 620
  - Lines: 624, 626, 628, 630, 631, 635, 637, 639, 641, 642
  - Lines: 646, 648, 650, 652, 654, 658, 660, 662, 664, 666
- **SafetyHandler.java**: 5 violations (lines 74, 131, 134, 137, 163)
- **UserManagementHandler.java**: 1 violation (line 58)

### Core Package (62):
- **AchievementService.java**: 10 violations
- **Conversation.java**: 2 violations
- **Match.java**: 2 violations
- **MatchQuality.java**: 8 violations
- **MatchQualityService.java**: 15 violations
- **ProfileCompletionService.java**: 4 violations
- **ProfilePreviewService.java**: 5 violations
- **SwipeSession.java**: 2 violations
- **User.java**: 2 violations

### Storage Package (3):
- **H2UserStorage.java**: 3 violations (lines 297, 300, 303)

---

## CATEGORY 3: HIGH PRIORITY - Missing Javadoc (46)

Missing documentation on public APIs.

### CLI Package (22):
All handler classes missing class-level or method-level Javadoc.

### Core Package (20):
Core services and domain models missing documentation.

### Storage (4):
Database and Main classes missing Javadoc.

---

## CATEGORY 4: MEDIUM - Indentation (25)

Incorrect indentation in switch statements and records.
**Note**: Most can be auto-fixed with `mvn spotless:apply`

---

## CATEGORY 5: MEDIUM - Variable Naming (18)

### Abbreviation Violations (8):
- `userALastReadAt` → `userALastReadTime` (Conversation.java, H2ConversationStorage.java)

### Short Names (8):
- `aStr` → `userAString` (Conversation.java, Match.java)
- `bStr` → `userBString` (Conversation.java, Match.java)

### Delta Names (2):
- `dLat` → `deltaLatitude` (GeoUtils.java)
- `dLon` → `deltaLongitude` (GeoUtils.java)

---

## CATEGORY 6: MEDIUM - Variable Declaration Distance (13)

Variables declared too far from first usage.

---

## CATEGORY 7: LOW PRIORITY - Style (29)

- Line length > 100 chars: 10 violations
- Javadoc missing periods: 11 violations
- Unicode escapes: 6 violations
- Missing switch default: 1 violation
- Multiple declarations: 1 violation

---

## CATEGORY 8: CONFIGURATION (1)

Maven checkstyle plugin encoding parameter warning.

---

## Fixes in Progress

✅ **Completed**: Issue analysis and categorization
🔄 **In Progress**: Console encoding fixes
📋 **Planned**: All other categories

Last Updated: 2026-01-12
