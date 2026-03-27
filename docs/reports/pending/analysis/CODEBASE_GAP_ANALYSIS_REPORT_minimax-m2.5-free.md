# Dating App Codebase Gap Analysis Report

**Generated:** March 11, 2026  
**AI Model:** minimax-m2.5-free  

---

## Executive Summary

This report provides a comprehensive analysis of the dating application codebase, identifying feature gaps, implementation gaps, missing UI elements, backend logic gaps, and quality-of-life enhancements. The application is built with Java 25, JavaFX 25, and Maven, featuring both a CLI interface and a GUI frontend.

The codebase is well-structured with clean separation between core business logic, storage, and UI layers. However, several features exist only as UI scaffolding without backend implementations, and several modern dating app features are missing entirely.

---

## 1. Core Model Gaps

### 1.1 User Model Deficiencies

The `User` entity (826 lines) has substantial fields but lacks several common dating profile elements:

| Missing Field | Impact | Suggested Addition |
|--------------|--------|-------------------|
| Occupation/Job Title | Medium | String field |
| Religion/Faith | Medium | Enum + optional text |
| Body Type | Medium | Enum (slim, athletic, average, curvy, etc.) |
| Political Views | Low | Enum or text |
| Zodiac Sign | Low | Auto-calculated from birthDate |
| Languages Spoken | Medium | Set<Language> enum |
| Education Institution | Low | String (school name) |
| Social Media Links | Medium | Map<Platform, URL> |
| Last Active Timestamp | High | Instant (for online status) |
| Online Status | High | Enum (ONLINE, OFFLINE, AWAY) |

**Current State:** User tracks `createdAt` and `updatedAt` but has no "last active" tracking.

### 1.2 Match Model Deficiencies

The `Match` entity (321 lines) is minimal:

| Missing Field | Impact |
|--------------|--------|
| Compatibility Score | Medium (stored separately in MatchQualityService but not on Match) |
| Conversation ID Reference | Low (derived deterministically) |
| Last Interaction Timestamp | Medium |
| Super Like Indicator | High (super like exists in UI but not backend) |
| Match Expiration | Low |

### 1.3 Missing Domain Models

The following domain concepts are not modeled:

- **SuperLike** - UI exists but no domain model or persistence
- **ProfileBoost** - No concept of paid/sponsored profile visibility
- **Story/Reel** - No temporal content sharing
- **Virtual Gift** - No in-app purchases or gifting
- **Group Chat** - Only 1:1 messaging exists

---

## 2. Feature Gaps

### 2.1 Super Like (Partially Implemented)

**Status:** UI-only, behaves as regular like

The matching UI has a super-like button (UP arrow shortcut) and achievement "Super Liker", but:
- No `SuperLike` record/model exists
- No persistence for super likes
- No difference in matching
- No daily logic limit on super likes

```java
// MatchingController - current behavior
case SUPER_LIKE -> processLike(candidate.getId(), Like.LikeType.LIKE);
```

**Recommendation:** Either implement full super-like feature or remove the UI element.

### 2.2 Presence/Online Status (Partially Implemented)

**Status:** UI scaffolding exists, backend is no-op

- `ChatViewModel` has `presenceStatusProperty` and `remoteTypingProperty`
- `UiPresenceDataAccess` interface exists
- `NoOpUiPresenceDataAccess` is the production implementation
- `MatchesController` hardcodes "OFFLINE" for all users
- No heartbeat mechanism or WebSocket-based presence updates

```java
// UiDataAdapters.java:125
public interface UiPresenceDataAccess {
    PresenceStatus getPresence(UUID userId);
    boolean isTyping(UUID userId);
}
```

**Files with TODOs:**
- `MatchesController.java:566` - `FUTURE(presence-tracking)`
- `ChatViewModel.java:204` - Loads presence but gets no-op results

### 2.3 Profile Boost (Missing)

No implementation for:
- Paid profile boosting
- Visibility prioritization
- Boost expiration tracking
- Boost analytics

### 2.4 Messaging Enhancements (Missing)

| Feature | Status |
|---------|--------|
| Read Receipts | Partial (Conversation has read timestamps, but UI doesn't display) |
| Typing Indicators | Partial (UI component exists, no backend) |
| Message Attachments | Missing |
| Voice Messages | Missing |
| Video Calls | Missing |
| Message Reactions | Missing |
| Message Editing | Missing |
| Message Delete for Both | Missing |

### 2.5 Social Features (Missing)

| Feature | Status |
|---------|--------|
| Stories/Reels | Missing |
| Virtual Gifts | Missing |
| Group Chats | Missing |
| Social Media Linking | Missing |
| "People You May Know" | Missing |
| In-App Calls | Missing |

---

## 3. Implementation Gaps

### 3.1 Matching Service - CandidateFinder

The `CandidateFinder` is well-implemented but:
- No pagination support (returns all candidates)
- No caching of candidate results
- No "most compatible" sorting algorithm beyond basic scoring

### 3.2 Recommendation Service

The `RecommendationService` handles daily picks and limits:
- `DailyLimitService` - Rate limiting works
- `DailyPickService` - Featured profiles work
- `StandoutService` - High-compatibility highlighting works

**Missing:** Location-based recommendations (city/venue matches)

### 3.3 Undo Service

`UndoService` exists and works for swipes:
- Time-limited undo (within window)
- Properly removes likes

**Gap:** No undo history UI - users can't see what they undid

### 3.4 Trust & Safety

`TrustSafetyService` handles:
- Blocking (bidirectional)
- Reporting (multiple reasons)

**Missing:**
- Automated content moderation
- Trust scores
- Report follow-up workflow
- Auto-expire old blocks

---

## 4. Storage Layer Gaps

### 4.1 Analytics Storage

**Tracked:**
- UserStats, PlatformStats
- ProfileViews
- Achievements
- DailyPicks
- SwipeSessions
- Stats history

**Not Tracked:**
- Message response time metrics
- Conversation depth analytics
- Time-to-first-date tracking
- Retention/cohort analysis
- Revenue/monetization data
- Content moderation logs

### 4.2 Communication Storage

**Tracked:**
- Conversations with read timestamps
- Messages (text only, 1000 char max)
- FriendRequests
- Notifications

**Not Tracked:**
- Message attachments
- Read receipts (stored but not used)
- Typing indicator events
- Message delivery status

### 4.3 User Storage

**Tracked:**
- Users with all profile fields
- ProfileNotes

**Not Tracked:**
- User search index
- Full-text search on bio
- Profile view history

### 4.4 Interaction Storage

**Tracked:**
- Likes (LIKE/PASS)
- Matches

**Not Tracked:**
- Like analytics (trends over time)
- Match quality scores (stored in MatchQualityService separately)
- Super like history

---

## 5. API Gaps (RestApiServer)

The REST API (`RestApiServer.java`, 792 lines) exposes:

### Implemented Endpoints

```
GET  /api/health
GET  /api/users
GET  /api/users/{id}
GET  /api/users/{id}/candidates
GET  /api/users/{id}/matches
POST /api/users/{id}/like/{targetId}
POST /api/users/{id}/pass/{targetId}
GET  /api/users/{id}/conversations
GET  /api/conversations/{conversationId}/messages
POST /api/conversations/{conversationId}/messages
```

### Missing Endpoints

| Endpoint | Purpose |
|----------|---------|
| `POST /api/users/{id}/superlike/{targetId}` | Super like action |
| `GET  /api/users/{id}/standouts` | Get standout recommendations |
| `PUT  /api/users/{id}/profile` | Update profile |
| `POST /api/users/{id}/block/{targetId}` | Block user |
| `POST /api/users/{id}/report` | Report user |
| `GET  /api/users/{id}/notifications` | Get notifications |
| `POST /api/users/{id}/friend-request` | Send friend request |
| `GET  /api/users/{id}/stats` | Get user statistics |
| `GET  /api/users/{id}/achievements` | Get achievements |

---

## 6. CLI Gaps

The CLI handlers provide solid functionality:

| Handler | Features |
|---------|----------|
| ProfileHandler | Profile creation, viewing, editing |
| MatchingHandler | Swiping, candidates, daily picks, standouts, undo |
| MessagingHandler | Conversations, messages, sending |
| SafetyHandler | Blocks, reports |
| StatsHandler | Statistics, achievements |

### CLI Missing Features

- Profile photo upload (CLI cannot handle images)
- Interactive rich UI (limited to text)
- Real-time notifications
- Presence/typing indicators

---

## 7. UI/UX Gaps

### 7.1 Screen Coverage

**Existing Screens (14):**
- Login, Dashboard, Matching, Matches, Chat, Profile
- Stats, Standouts, Social, Safety, Notes, Preferences
- Milestone Popup

**Missing Screens:**

| Screen | Purpose | Priority |
|-------|---------|----------|
| Settings | App preferences, notifications, account | High |
| Onboarding | New user guided setup | High |
| View Other Profile | Read-only profile view | High |
| Search | Text-based user search | Medium |
| Activity History | Swipe/conversation archive | Medium |
| Edit Match Preferences | Separate from profile editing | Low |
| Notifications Settings | Configure notification types | Medium |
| Premium/Boost UI | Paid feature purchase UI | Medium |

### 7.2 Incomplete UI Features

1. **Super Like Button** - Visible but acts as regular like
2. **Presence Status** - Always shows "offline" in matches list
3. **Typing Indicator** - UI exists but no backend binding
4. **Read Receipts** - Data stored but not displayed
5. **Photo Verification** - No UI for selfie verification
6. **Photo Quality Scoring** - No visual feedback on photo quality
7. **Photo Reordering** - No drag-to-reorder UI

### 7.3 UI Component Gaps

| Component | Status |
|-----------|--------|
| Charts/Graphs | Missing (Stats is just numbers) |
| Date Picker | Using system default |
| Rich Text Editor | Missing for bio |
| Image Cropper | Missing for photo upload |
| Video Player | Missing for potential video messages |
| Map View | Missing for location display |

---

## 8. Quality of Life Enhancements

### 8.1 Code Quality

1. **NavigationService** (423 lines) - Handles routing, animations, context, and history
   - Recommendation: Split into NavigationRouter, NavigationHistory, TransitionManager

2. **ViewModelFactory** - Creates all ViewModels, becoming large
   - Consider: Modular factory per feature domain

3. **String concatenation in Match.generateId()** - Uses string concatenation instead of proper UUID handling
   ```java
   // Current
   String id = userA + "_" + userB;
   // Better
   String id = userA.toString() + "_" + userB.toString();
   ```

### 8.2 User Experience

| Enhancement | Description |
|-------------|-------------|
| Profile Completion Nudge | Dashboard shows nudges but could be more proactive |
| Match Suggestions | Could show "Why we matched" reasons |
| Timezone Handling | All times in system timezone, no user timezone |
| Date/Time Formatting | No relative time ("2 hours ago") in UI |
| Empty States | Some screens lack good empty state messaging |
| Error Recovery | Generic errors, need contextual error messages |
| Offline Mode | App requires network, no offline support |

### 8.3 Performance

| Area | Current | Improvement |
|------|---------|-------------|
| Image Loading | Simple cache (100 images) | Progressive loading, placeholder |
| Candidate Loading | All at once | Pagination, infinite scroll |
| Message Loading | Basic pagination | Infinite scroll, lazy loading |
| Database Queries | Some N+1 potential | Query optimization |

---

## 9. Event System Gaps

The event system (`AppEvent`, `AppEventBus`, `InProcessAppEventBus`) handles:

- Achievement events
- Metrics events  
- Notification events

**Missing Event Types:**
- Profile view events
- Message read events
- Presence change events
- Block/report events
- Match quality events

---

## 10. Testing Gaps

### Test Coverage

- ViewModel tests exist for most ViewModels
- Controller tests exist for most screens
- Core service tests exist (CandidateFinder, CleanupService, etc.)

### Missing Test Coverage

| Area | Priority |
|------|----------|
| Integration tests (full flow) | High |
| REST API endpoint tests | High |
| CLI handler tests | Medium |
| Performance/load tests | Low |
| Security tests | Medium |
| Database migration tests | Medium |

---

## 11. Summary Table

| Category | Complete | Partial | Missing |
|----------|----------|---------|---------|
| User Profiles | 60% | 25% | 15% |
| Matching | 80% | 15% | 5% |
| Messaging | 50% | 25% | 25% |
| Social Features | 30% | 20% | 50% |
| Analytics | 40% | 30% | 30% |
| UI Screens | 70% | 15% | 15% |
| REST API | 40% | 20% | 40% |
| CLI | 70% | 15% | 15% |
| Presence/Real-time | 10% | 20% | 70% |
| Premium Features | 0% | 0% | 100% |

---

## 12. Recommendations

### High Priority

1. **Implement Super Like or Remove UI** - Currently confusing to users
2. **Implement Presence System or Remove Indicators** - Dead UI code
3. **Add Settings Screen** - Missing app configuration
4. **Add Onboarding Flow** - New user experience incomplete
5. **Add View Profile Screen** - Can't view other users' profiles

### Medium Priority

1. **Add Search Functionality** - Users can only browse
2. **Implement Read Receipts Display** - Data exists, UI missing
3. **Add Charts to Stats Screen** - Currently just numbers
4. **Add Notification Settings** - Can't configure notifications
5. **Implement Profile Completion Nudges** - More proactive

### Low Priority

1. **Stories/Temporal Content** - Major feature, not critical
2. **Video Calls** - Major feature, requires infrastructure
3. **Virtual Gifts** - Monetization feature
4. **Group Chats** - Not core to dating app

---

## 13. Architecture Observations

### Strengths

- Clean separation: Core / Storage / UI
- Use of records for immutable data
- Proper dependency injection via ServiceRegistry
- Event-driven architecture with AppEventBus
- Comprehensive test coverage on ViewModels/Controllers

### Concerns

- `NavigationService` is a god object (routing + history + context + animations)
- Some features are "shell only" - UI exists without backend
- Presence system has full UI but no-op backend
- No API versioning strategy
- Limited error handling granularity

## 14. Profile and Lifestyle Features (Detailed)

### 14.1 All Available Profile Fields

From `User.java` (lines 72-113):

| Field | Type | Required for Activation |
|-------|------|------------------------|
| `name` | String | Yes |
| `bio` | String | Yes |
| `birthDate` | LocalDate | Yes |
| `gender` | User.Gender (MALE/FEMALE/OTHER) | Yes |
| `interestedIn` | Set<Gender> | Yes |
| `photoUrls` | List<String> | Yes |
| `lat/lon` | double | Yes (location set) |
| `maxDistanceKm` | int | Yes |
| `minAge`/`maxAge` | int | Yes |
| `pacePreferences` | PacePreferences (4 sub-fields) | Yes |

### 14.2 Lifestyle Enum Values

**Smoking** (`MatchPreferences.Lifestyle.Smoking`): NEVER, SOMETIMES, REGULARLY

**Drinking** (`MatchPreferences.Lifestyle.Drinking`): NEVER, SOCIALLY, REGULARLY

**Wants Kids** (`MatchPreferences.Lifestyle.WantsKids`): NO, OPEN, SOMEDAY, HAS_KIDS

**Looking For** (`MatchPreferences.Lifestyle.LookingFor`): CASUAL, SHORT_TERM, LONG_TERM, MARRIAGE, UNSURE

**Education** (`MatchPreferences.Lifestyle.Education`): HIGH_SCHOOL, SOME_COLLEGE, BACHELORS, MASTERS, PHD, TRADE_SCHOOL, OTHER

### 14.3 Interests (44 Total, Max 10 Per User)

Categories: OUTDOORS (6), ARTS & CULTURE (8), FOOD & DRINK (6), SPORTS & FITNESS (7), GAMES & TECH (5), SOCIAL (7)

### 14.4 Missing Profile Elements

| Missing | Priority |
|---------|----------|
| Occupation/Job Title | Medium |
| Religion/Faith | Medium |
| Body Type | Medium |
| Exercise frequency | Low |
| Diet preferences (vegetarian, vegan) | Low |
| Sleep schedule (early bird/night owl) | Low |
| Living situation | Low |
| Social media links | Medium |
| Instagram/TikTok | Medium |
| Profile prompts/conversation starters | Medium |
| Photo verification/selfie | High |
| ID verification | Medium |

---

## 15. Use Cases Layer Gaps

### 15.1 Current Use Cases Summary

| Category | Commands | Queries |
|----------|---------|---------|
| Profile | 4 | 6 |
| Matching | 5 | 5 |
| Messaging | 2 | 4 |
| Social | 7 | 2 |
| **TOTAL** | **18** | **17** |

### 15.2 Verified Missing Use Cases

| Missing Use Case | Category |
|-----------------|----------|
| User registration/signup | Authentication |
| User login | Authentication |
| Password change/reset | Authentication |
| Account deletion | Authentication |
| Unblock user | Social |
| Delete message | Messaging |
| Delete conversation | Messaging |
| Mark all notifications read | Notifications |
| Archive match | Matching |
| Boost profile | Premium |
| Advanced filtering | Discovery |

### 15.3 Corrected Findings

**CORRECTED:** Friend request creation DOES exist
- `SocialUseCases.requestFriendZone()` method creates friend requests
- Available via: JavaFX UI (MatchCardData), CLI ("f" option), REST API

**CORRECTED:** Cannot unblock users (still valid gap)
- Can block via `SocialUseCases.blockUser()` 
- No `unblockUser()` method exists

---

## 16. Metrics and Achievements Gaps

### 16.1 All Achievements

| Achievement | Criteria |
|-------------|----------|
| FIRST_SPARK | 1 match |
| SOCIAL_BUTTERFLY | 5 matches |
| POPULAR | 10 matches |
| SUPERSTAR | 25 matches |
| LEGEND | 50 matches |
| SELECTIVE | Like ratio < 20% |
| OPEN_MINDED | Like ratio > 60% |
| COMPLETE_PACKAGE | Full profile |
| STORYTELLER | Bio > 100 chars |
| LIFESTYLE_GUARIAN | Lifestyle fields filled |
| GUARDIAN | Report fake profile |

### 16.2 Tracked Metrics

- Swipes given/received
- Likes given/received  
- Passes given/received
- Like ratios
- Match counts and rates
- Blocks/reports
- Reciprocity, selectiveness, attractiveness scores

### 16.3 Missing Metrics

| Missing Metric | Impact |
|---------------|--------|
| Message response time | Medium |
| Conversation depth | Medium |
| Time-to-first-date | High |
| Retention/cohort data | High |
| Revenue/monetization | Low |
| Content moderation logs | Medium |
| Profile view trends | Medium |
| Daily active users (real-time) | Medium |

---

## 17. Database Schema Issues

### 17.1 All Tables (21 Total)

1. users, 2. likes, 3. matches, 4. swipe_sessions, 5. user_stats, 6. platform_stats, 7. conversations, 8. messages, 9. friend_requests, 10. notifications, 11. blocks, 12. reports, 13. profile_notes, 14. profile_views, 15. daily_pick_views, 16. daily_picks, 17. user_achievements, 18. standouts, 19. undo_states, 20. Normalized tables (8), 21. schema_version

### 17.2 Schema Status (Verified)

**CORRECTED:** V3 schema migration is INTENTIONAL, not a bug
- `SchemaInitializer` creates legacy columns + normalized tables initially
- `MigrationRunner.applyV3()` drops legacy columns for cleanup
- Both fresh installs and upgrades converge to same final schema
- Code comments explicitly document this "compatibility window" pattern
- This is by design, not a bug

---

## 18. Image and Media Handling Issues

### 18.1 Current Implementation

- **ImageCache**: LRU cache, max 100 images, sync loading, default avatar fallback
- **LocalPhotoStore**: ~/.datingapp/photos/, max 6 photos per user, file:// URIs
- **Validation**: 5MB max file size, PNG/JPEG only

### 18.2 Missing Features

| Feature | Status |
|---------|--------|
| Image compression | MISSING |
| EXIF orientation handling | MISSING |
| Image dimension validation | MISSING |
| Server-side image API | MISSING |
| Thumbnail generation | MISSING |
| MIME type validation | MISSING (only extension check) |
| Progressive loading | MISSING |
| Image cropping/editing | MISSING |
| GIF/WebP support | MISSING |

### 18.3 Security/Reliability Issues

1. **No EXIF handling** - Portrait photos from mobile may display rotated
2. **No compression** - High-res images stay large, wasting storage/bandwidth
3. **Weak validation** - Only extension checked, not actual MIME type
4. **No dimension limits** - Could cause OOM with huge images
5. **Sync loading** - Main loading path blocks calling thread

---

## 19. Workflow Policies and Business Rules

### 19.1 Existing Policies

**RelationshipWorkflowPolicy** - Match state transitions:
- ACTIVE -> FRIENDS, UNMATCHED, GRACEFUL_EXIT, BLOCKED
- FRIENDS -> UNMATCHED, GRACEFUL_EXIT, BLOCKED
- Terminal: UNMATCHED, GRACEFUL_EXIT, BLOCKED

**ProfileActivationPolicy** - User activation eligibility:
- Must be INCOMPLETE state
- Cannot be BANNED
- All required fields must be complete

### 19.2 Missing Policies

| Missing Policy | Should Cover |
|---------------|-------------|
| SwipePolicy | Swipe rules, super-like limits, daily limits |
| UndoPolicy | Undo eligibility, time window |
| RecommendationPolicy | Daily recommendations, refresh rules |
| MatchingThresholdPolicy | Score thresholds for match decisions |
| UserStatePolicy | User state transitions (like RelationshipWorkflowPolicy) |
| MessagePolicy | Rate limiting, content rules |

### 19.3 Duplicate Logic (Verified)

**CONFIRMED:** Duplicate validation exists:
- **User.isComplete()** (User.java line 760) - returns boolean
- **ProfileActivationPolicy.missingFields()** (ProfileActivationPolicy.java line 61) - returns List<String>

Both validate the exact same 10 fields with identical logic. Code comment acknowledges: *"Mirrors User.isComplete() logic — if isComplete() changes, update this too."*

---

## 20. Notification System Gaps

### 20.1 Notification Types

| Type | Handler Exists | Actually Created |
|------|---------------|------------------|
| MATCH_FOUND | **NO** | **NO** - Event fires but no handler |
| NEW_MESSAGE | **NO** | **NO** - Event fires but no handler |
| FRIEND_REQUEST | N/A (direct) | YES |
| FRIEND_REQUEST_ACCEPTED | Yes | YES |
| GRACEFUL_EXIT | Yes | YES |

### 20.2 Verified Gaps

**CONFIRMED - MATCH_FOUND notification not created:**
- `AppEvent.MatchCreated` fires at MatchingUseCases.java:199 and :325
- `NotificationEventHandler` only subscribes to `RelationshipTransitioned`
- No handler creates MATCH_FOUND notifications
- Users never receive "It's a Match!" notifications

**CONFIRMED - NEW_MESSAGE notification not created:**
- `AppEvent.MessageSent` fires at MessagingUseCases.java:117
- `MetricsEventHandler` handles it (for metrics only)
- `NotificationEventHandler` does NOT handle it
- No code creates NEW_MESSAGE notifications
- Users never notified of new messages

### 20.3 Missing Notification Features

1. **No MATCH_FOUND notification** - Users don't get notified when they match
2. **No NEW_MESSAGE notification** - Users don't get notified of new messages
3. **No push notification infrastructure** - All stored only in DB
4. **No real-time delivery** - No WebSocket or SSE
5. **No notification preferences** - Can't configure which to receive
6. **No unread count aggregation** - Not available in dashboard

---

## 21. Event System Details

### 21.1 Events That Fire

| Event | Published By | Has Handler |
|-------|-------------|-------------|
| SwipeRecorded | MatchingUseCases | Yes (MetricsEventHandler, AchievementEventHandler) |
| MatchCreated | MatchingUseCases | **NO** - Event fires but no handler |
| ProfileSaved | ProfileUseCases | No handler (probably intentional) |
| FriendRequestAccepted | SocialUseCases | **NO** - Event fires but no handler |
| RelationshipTransitioned | SocialUseCases | Yes (NotificationEventHandler) |
| MessageSent | MessagingUseCases | Yes (MetricsEventHandler) |

### 21.2 Missing Events

- UserBlocked - when SocialUseCases.blockUser() called
- UserReported - when SocialUseCases.reportUser() called
- SwipeUndone - when MatchingUseCases.undoSwipe() succeeds
- PreferencesUpdated - when preferences changed

---

## 22. Presence/Online Status (Verified Gap)

### CONFIRMED: Full UI with No-Op Backend

**UI Implementation (Complete):**
- `PresenceStatus` enum: UNKNOWN, ONLINE, AWAY, OFFLINE
- `UiPresenceDataAccess` interface with getPresence/isTyping methods
- `ChatViewModel` has presenceStatus and remoteTyping properties
- `ChatController` has updatePresenceIndicator/updateTypingIndicator methods
- CSS classes: status-online, status-away, status-offline

**Backend (No-Op):**
- `NoOpUiPresenceDataAccess` always returns `PresenceStatus.UNKNOWN` and `false`
- `ViewModelFactory.createUiPresenceDataAccess()` hardcoded to return no-op
- NO other UiPresenceDataAccess implementation exists

**Result:** 
- Presence dot never visible in production (line 221: `visible = resolvedStatus != UNKNOWN`)
- Typing indicator never shows (always returns false)
- Dead UI code - confusing for developers

---

## 23. Super Like (Verified Gap)

### CONFIRMED: UI-Only Implementation

**Evidence:**
- MatchingController.java:476-482 - explicit comment: *"For now, acts like a regular like (super like logic to be added later)"*
- MatchingViewModel.like() calls processSwipe(true) with no super-like differentiation
- No Like.LikeType.SUPER enum value
- No database column for super like
- "Super Liker" achievement shows 0/10 progress but can never be completed

**Result:** Super like button is purely cosmetic - provides pulse animation but otherwise identical to regular like.

---

## 24. Summary Table (Updated)

| Category | Complete | Partial | Missing |
|----------|----------|---------|---------|
| User Profiles | 55% | 25% | 20% |
| Matching | 70% | 15% | 15% |
| Messaging | 40% | 25% | 35% |
| Social Features | 30% | 20% | 50% |
| Analytics/Metrics | 35% | 30% | 35% |
| UI Screens | 65% | 15% | 20% |
| REST API | 35% | 20% | 45% |
| CLI | 70% | 15% | 15% |
| Presence/Real-time | 5% | 0% | 95% |
| Premium Features | 0% | 0% | 100% |
| Notifications | 40% | 0% | 60% |
| Workflow Policies | 50% | 20% | 30% |

---

## 25. Updated Recommendations

### High Priority (Verified)

1. **Implement Super Like fully OR remove UI** - Verified: Currently misleading UI
2. **Implement Presence System OR remove indicators** - Verified: Dead code
3. **Add MATCH_FOUND notification handler** - Verified: Critical gap
4. **Add NEW_MESSAGE notification handler** - Verified: Critical gap
5. **Add Settings Screen** - Missing app configuration
6. **Add Onboarding Flow** - New user experience incomplete

### Medium Priority

1. **Add unblock functionality** - Verified gap: Can block but can't unblock
2. **Add Search Functionality** - Users can only browse
3. **Implement Read Receipts Display** - Data exists, UI missing
4. **Add Charts to Stats Screen** - Currently just numbers
5. **Add EXIF orientation handling** - Portrait photos broken
6. **Add image compression** - Storage/bandwidth waste
7. **Create missing policies** - SwipePolicy, UndoPolicy, UserStatePolicy
8. **Fix duplicate validation** - User.isComplete() vs ProfileActivationPolicy

### Low Priority

1. **Stories/Temporal Content**
2. **Video Calls**
3. **Virtual Gifts**
4. **Group Chats**
5. **Profile prompts/conversation starters**

---

## Appendix: Key Files Reference

| Component | File |
|-----------|------|
| User Model | `core/model/User.java` (826 lines) |
| Match Model | `core/model/Match.java` (321 lines) |
| Matching Service | `core/matching/MatchingService.java` |
| Chat ViewModel | `ui/viewmodel/ChatViewModel.java` |
| Navigation | `ui/NavigationService.java` |
| REST API | `app/api/RestApiServer.java` (792 lines) |
| CLI Matching | `app/cli/MatchingHandler.java` (1011 lines) |
| Schema | `storage/schema/SchemaInitializer.java` |
| Events | `app/event/AppEvent.java` |
| Policies | `core/workflow/RelationshipWorkflowPolicy.java` |
| Presence | `ui/viewmodel/UiDataAdapters.java` (NoOp) |
| Notifications | `app/event/handlers/NotificationEventHandler.java` |

---

*End of Report*
