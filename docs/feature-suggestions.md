# Feature Suggestions for Dating App CLI

**Generated:** 2026-01-09
**Current Phase:** 0.5B (Console App)
**Based on:** Comprehensive codebase analysis

---

## Summary

This document proposes new features appropriate for the project's current state. Features are organized by priority and complexity, taking into account the existing architecture (2-layer core + storage), implemented capabilities, and pending phase-1 features.

---

## 🔥 High Priority Features

### 1. Interests/Hobbies System *(Planned but not implemented)*
**Complexity:** Medium | **Effort:** ~400 LOC

Implement the predefined tagging system for user interests that:
- Adds 30+ interests across 6 categories (Outdoors, Arts, Food, Sports, Tech, Social)
- Enhances match quality scoring via Jaccard similarity calculation
- Displays shared interests on candidate cards ("🎯 Shared: Hiking, Coffee")
- **Critical:** Currently `MatchQualityService.computeInterestScore()` returns placeholder 0.5

### 2. Super Like Feature
**Complexity:** Low | **Effort:** ~150 LOC

Allow users to express stronger interest with a daily "Super Like":
- Limited to 1 per day (configurable via `AppConfig`)
- Notifies the recipient that they were super-liked
- Boosts visibility in the recipient's candidate queue
- New `Like.Direction.SUPER_LIKE` enum value
- CLI prompt: `[L]ike / [P]ass / [S]uper Like (1 left today)`

### 3. Match Expiration / Conversation Starter
**Complexity:** Medium | **Effort:** ~250 LOC

Address stale matches with engagement prompts:
- Track time since match creation (`Match.createdAt`)
- After 48 hours with no activity, show "Break the ice!" prompt
- After 7 days, optionally expire or deprioritize match
- Add `Match.lastMessageAt` field for future messaging integration
- CLI enhancement: `⏰ 3 matches need attention!`

---

## ⭐ Medium Priority Features

### 4. Random Match of the Day *(Planned but not implemented)*
**Complexity:** Low | **Effort:** ~300 LOC

Present a daily "wild card" profile that bypasses normal filters:
- Deterministic selection using date + user ID as seed
- Partially respects preferences (gender yes, age/distance no)
- Separate from regular browsing flow
- Can be skipped and revisited until swiped

### 5. Smart Prompts / Icebreakers
**Complexity:** Low | **Effort:** ~120 LOC

Help users craft engaging profiles with prompts:
- "Two truths and a lie..."
- "The way to my heart is..."
- "My most controversial opinion is..."
- Stored as `List<UserPrompt>` on User entity
- Displayed in candidate cards below bio

### 6. Photo Verification Badge
**Complexity:** Medium | **Effort:** ~200 LOC

Increase trust with verified profiles:
- New `User.verified` boolean field
- CLI admin command to mark users as verified
- Display ✓ badge on candidate cards: "Sarah ✓"
- Future: Integration with photo verification service

### 7. "Looking For" Compatibility Matching
**Complexity:** Low | **Effort:** ~100 LOC

Enhance `CandidateFinder` to filter by relationship goals:
- New dealbreaker option: "Only show users looking for same thing"
- Existing `Lifestyle.LookingFor` enum already supports this
- Add to `DealbreakersEvaluator` as optional filter
- Display match in candidate card: "Also looking for: Long-term"

### 8. Pause Discovery Mode
**Complexity:** Low | **Effort:** ~80 LOC

Allow users to temporarily hide from others:
- Distinct from `User.State.PAUSED` (which pauses the account)
- New `User.discoveryPaused` boolean
- Paused users:
  - Can still browse candidates
  - Don't appear in others' candidate lists
  - Can still match if they like someone who already liked them
- CLI: `8. 👻 Toggle discovery (Currently: Visible)`

---

## 🎨 Engagement & Gamification

### 9. Achievement System *(Planned but not implemented)*
**Complexity:** High | **Effort:** ~500 LOC

Gamification via milestone badges:
- **Matching:** First Spark (1 match), Social Butterfly (5), Popular (10)
- **Profile:** Complete Package (100% profile), Storyteller (100+ char bio)
- **Behavior:** Selective (<20% like ratio), Open-Minded (>60% ratio)
- New `Achievement` enum and `UserAchievement` record
- CLI: `🏆 ACHIEVEMENT UNLOCKED: 💫 First Spark`

### 10. Weekly Engagement Report
**Complexity:** Medium | **Effort:** ~180 LOC

Provide users with personalized activity summaries:
- Total swipes, likes received, match rate
- Profile view count (simulated for CLI)
- "Your best day was Tuesday with 3 new matches"
- Builds on existing `StatsService` and `UserStats`
- CLI: `11. 📊 Weekly report`

### 11. Mutual Friends Indicator *(Placeholder)*
**Complexity:** High | **Effort:** ~300 LOC

Show social connections between users:
- Requires social graph data model
- Initially: Just the UI placeholder
- Future: OAuth integration with social platforms
- Display: "👥 2 mutual connections"

---

## 🔒 Safety & Trust

### 12. Enhanced Reporting with Categories
**Complexity:** Low | **Effort:** ~100 LOC

Improve existing `ReportService` with detailed categories:
- Current: Single `Report.Reason` enum
- Add: `Report.details` free-text field
- Add: Escalation priority (SPAM vs HARASSMENT)
- CLI: Multi-step reporting wizard

### 13. Block History / Manage Blocks
**Complexity:** Low | **Effort:** ~80 LOC

Allow users to view and manage their blocks:
- New `BlockStorage.getBlocksBy(UUID userId)` method
- CLI menu: `View users I've blocked (5 users)`
- Option to unblock (creates new interaction entry)

### 14. Profile Verification Prompts
**Complexity:** Low | **Effort:** ~60 LOC

Encourage photo authenticity:
- During profile completion, prompt for real photo
- Display warning if photo URL looks like stock image
- Add tip: "Profiles with real photos get 6x more matches"

---

## 🛠 Technical Improvements

### 15. Candidate Pagination
**Complexity:** Medium | **Effort:** ~150 LOC

Handle large candidate pools efficiently:
- Current: `CandidateFinder` returns all matches
- Add: `findCandidates(User seeker, int limit, int offset)`
- CLI: "Load more candidates (15 remaining)"
- Reduces memory for large user bases

### 16. Profile Change History
**Complexity:** Medium | **Effort:** ~200 LOC

Track profile edits for moderation:
- New `ProfileChangeLog` entity
- Store: field changed, old value, new value, timestamp
- Admin use: Detect suspicious rapid changes
- Storage: `H2ProfileChangeLogStorage`

### 17. Batch Undo (Multi-Step History)
**Complexity:** Medium | **Effort:** ~180 LOC

Extend undo beyond last swipe:
- Current: Only most recent swipe undoable
- New: Ring buffer of last N swipes (default 5)
- `UndoService.getUndoHistory()` returns list
- CLI: `Select swipe to undo: 1. Alice (liked) 2. Bob (passed)`

### 18. Configuration Export/Import
**Complexity:** Low | **Effort:** ~100 LOC

Allow users to backup/restore settings:
- Export dealbreakers, preferences as JSON
- Import to restore configuration
- Useful for testing and user support
- `AppConfig.toJson()` / `AppConfig.fromJson()`

---

## 📊 Analytics & Insights

### 19. Match Quality Trends
**Complexity:** Low | **Effort:** ~120 LOC

Track match quality over time:
- Store `MatchQuality` scores with matches
- Show trend: "Your match quality is improving! ↑12% this week"
- Identify patterns: "You match best with nearby users"

### 20. Swipe Behavior Insights
**Complexity:** Medium | **Effort:** ~150 LOC

Provide personalized feedback to users:
- Like rate by time of day
- Average session duration trend
- "You're most active on weekends"
- Builds on `SwipeSession` data

---

## Implementation Priority Matrix

| Feature | Priority | Complexity | Dependencies |
|---------|----------|------------|--------------|
| Interests/Hobbies | 🔥 High | Medium | None |
| Super Like | 🔥 High | Low | None |
| Match Expiration | 🔥 High | Medium | None |
| Random Match of Day | ⭐ Medium | Low | None |
| Smart Prompts | ⭐ Medium | Low | None |
| Pause Discovery | ⭐ Medium | Low | None |
| Achievement System | 🎨 Medium | High | None |
| Candidate Pagination | 🛠 Medium | Medium | None |

---

## Recommended Next Steps

1. **Implement Interests/Hobbies** — Already designed, fills gap in match quality scoring
2. **Add Super Like** — Low-effort, high-engagement feature
3. **Complete Random Match of Day** — Already designed, adds serendipity
4. **Build Achievement System** — Gamification drives engagement

---

*Note: All features are designed to maintain the 2-layer architecture (pure Java core, H2 storage) and CLI-first approach established in Phase 0.*
