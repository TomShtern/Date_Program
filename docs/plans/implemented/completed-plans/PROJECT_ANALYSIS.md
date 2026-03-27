# Dating App Project Analysis

**Date:** 2026-01-10
**Phase:** 1.5
**Lines of Code:** ~11,700 Java | 62 source files | 29 test files
**Tests:** All passing

---

## 🔴 Critical Problems to Fix (15)

| # | Problem | Location | Impact |
|---|---------|----------|--------|
| 1 | **ProfileHandler is 668 lines** - violates single responsibility | `cli/ProfileHandler.java` | Hard to maintain, high cognitive load |
| 2 | **75 lines of code duplication** - 6 identical `copyExcept*()` methods | `ProfileHandler.java:600-667` | Bug fixes must be applied 6 times |
| 3 | **MatchingHandler is 524 lines** with mixed display/business logic | `cli/MatchingHandler.java` | Tight coupling, hard to test display |
| 4 | **Missing `StatsServiceTest`** - untested service | `core/StatsService.java` | Business logic not under test |
| 5 | **`save()` method is 104 lines** - god method | `H2UserStorage.java:32-106` | Hard to understand, maintain, test |
| 6 | **`mapUser()` method is 88 lines** - complex deserialization | `H2UserStorage.java:160-248` | Error-prone, hard to debug |
| 7 | **Serialization logic scattered** across storage classes | `storage/*.java` | Duplication, inconsistent handling |
| 8 | **DatabaseManager singleton** makes testing harder | `storage/DatabaseManager.java` | Integration tests need workarounds |
| 9 | **UndoService stores state in-memory** - lost on restart | `core/UndoService.java` | Undo unavailable after app restart |
| 10 | **No transaction support** in undo cascade deletes | `UndoService.java:132-140` | Inconsistent state on partial failure |
| 11 | **Inconsistent naming**: `DealbreakersEvaluator`, `InterestMatcher` | `core/*.java` | Breaks `*Service` naming convention |
| 12 | **CandidateFinder interface + impl both in core/** | `core/CandidateFinder*.java` | Unexpected pattern, not documented |
| 13 | **Bug investigation tests in production suite** | `BugInvestigationTest.java` | Clutters test reports |
| 14 | **No input validation** on CLI for edge cases | `cli/*Handler.java` | Invalid input can cause crashes |
| 15 | **Hardcoded password** in DatabaseManager | `DatabaseManager.java:34` | Security warning, not production-ready |

---

## 🟡 Next Changes to Do (15)

| # | Change | Priority | Effort |
|---|--------|----------|--------|
| 1 | Add `mergeFrom()` to `Dealbreakers.Builder` | HIGH | 1h |
| 2 | Extract `DealbreakersEditor` helper class from ProfileHandler | HIGH | 2h |
| 3 | Create `SerializationUtils` for storage layer | HIGH | 2h |
| 4 | Create `UserMapper` to extract DB mapping logic | HIGH | 2h |
| 5 | Extract `MatchDisplayFormatter` from MatchingHandler | MEDIUM | 2h |
| 6 | Write `StatsServiceTest.java` | HIGH | 2h |
| 7 | Add section comments to `User.java` (400+ lines) | LOW | 0.5h |
| 8 | Document CandidateFinder design decision in JavaDoc | MEDIUM | 0.5h |
| 9 | Rename `DealbreakersEvaluator` → `DealbreakersService` | LOW | 1h |
| 10 | Rename `InterestMatcher` → `InterestMatchingService` | LOW | 1h |
| 11 | Archive or delete bug investigation tests | LOW | 0.5h |
| 12 | Add missing tests: `UserAchievementTest`, `PlatformStatsTest` | LOW | 1h |
| 13 | Reduce MatchingHandler `viewMatches()` from 70 lines | MEDIUM | 1h |
| 14 | Add `package-info.java` documentation to packages | LOW | 2h |
| 15 | Extract session display from `Main.printMenu()` | LOW | 0.5h |

---

## 🟢 Next Features to Implement (15)

*Building upon existing infrastructure, in logical order:*

| # | Feature | Builds On | Complexity |
|---|---------|-----------|------------|
| 1 | **Super Like** - special like type with notification | `Like.Direction` enum, `MatchingService` | LOW |
| 2 | **Rewind (Extended Undo)** - undo last 5 swipes persistently | `UndoService`, `LikeStorage` | MEDIUM |
| 3 | **"Who Liked Me"** - show users who liked current user | `LikeStorage.getLikesFor()` | LOW |
| 4 | **Standouts Grid** - curated 10 profiles daily | `DailyPickService`, `MatchQualityService` | MEDIUM |
| 5 | **Profile Boost** - temporary visibility increase | `CandidateFinder`, new `BoostService` | MEDIUM |
| 6 | **Match Expiration** - matches expire after 7 days inactive | `Match.State`, `MatchStorage` | LOW |
| 7 | **Second Look** - revisit passed profiles after 30 days | `LikeStorage`, `CandidateFinder` | LOW |
| 8 | **Question Prompts** - answer 3 prompts shown on profile | `User` fields, `ProfileHandler` | MEDIUM |
| 9 | **Compatibility Questions** - 20 questions for match scoring | `MatchQualityService` | HIGH |
| 10 | **Daily Login Streak** - achievement for consecutive days | `AchievementService`, new tracking | LOW |
| 11 | **Advanced Filters** - pre-filter by education, height | `CandidateFinder`, CLI | MEDIUM |
| 12 | **Photo Verification** - verify profile photos | `User` field, new service | MEDIUM |
| 13 | **Incognito Mode** - only visible to liked profiles | `CandidateFinder` filter | LOW |
| 14 | **Block List Import** - auto-block from contact list | `BlockStorage`, CLI flow | LOW |
| 15 | **Travel Mode** - hide profile while traveling | `User.State`, `CandidateFinder` | LOW |

---

## 💡 Additional Suggestions / Nice-to-Have (15)

| # | Suggestion | Benefit |
|---|------------|---------|
| 1 | Add OpenTelemetry/metrics for service operations | Observability |
| 2 | Create CLI test harness with simulated input | Automated GUI testing |
| 3 | Add JaCoCo for code coverage reports | Quality metrics |
| 4 | Consider PostgreSQL adapter for production | Scalability |
| 5 | Add API documentation (JavaDoc generation) | Developer onboarding |
| 6 | Implement proper logging levels (DEBUG, INFO) | Better debugging |
| 7 | Create database migration system (Flyway/Liquibase) | Schema versioning |
| 8 | Add rate limiting/throttling to prevent abuse | Security |
| 9 | Create config file support (YAML/properties) | Deployment flexibility |
| 10 | Add integration test for full CLI flow | End-to-end validation |
| 11 | Consider caching for MatchQualityService results | Performance |
| 12 | Add health check endpoint concept | Monitoring readiness |
| 13 | Create data export functionality (GDPR) | Compliance |
| 14 | Add input sanitization for bio/name | Security |
| 15 | Consider async processing for heavy operations | User experience |

---

## 📝 Notes

### Architecture Strengths
- ✅ Clean 3-layer architecture (core → storage → cli)
- ✅ Zero framework imports in core/ package
- ✅ 10 storage interfaces with 10 H2 implementations
- ✅ Well-defined service boundaries
- ✅ 93% test coverage on classes

### Technical Debt Hotspots
1. **ProfileHandler.java** - Needs split into 2-3 focused classes
2. **H2UserStorage.java** - Serialization logic should be extracted
3. **MatchingHandler.java** - Display logic mixed with business logic

### Test Coverage Status
- **Unit Tests:** 27 classes covered
- **Integration Tests:** 2 H2 storage integration tests
- **Missing:** `StatsServiceTest`, `UserAchievementTest`, `PlatformStatsTest`
- **Archival Candidates:** `BugInvestigationTest`, `Round2BugInvestigationTest`

### Dependencies (Current Versions)
- Java 21, Maven 3.x
- H2 Database 2.2.224
- JUnit Jupiter 5.10.2
- SLF4J 2.0.12 + Logback 1.5.3
- Spotless 3.1.0 (Google Java Format)

---

## ▶️ Next Steps

### Immediate (This Week)
1. [ ] Add `StatsServiceTest.java` - highest priority missing test
2. [ ] Add `mergeFrom()` to `Dealbreakers.Builder` - enables cleanup
3. [ ] Extract `DealbreakersEditor` from ProfileHandler - biggest cleanup win

### Short-Term (Next 2 Weeks)
4. [ ] Create `SerializationUtils` and `UserMapper`
5. [ ] Extract display logic from `MatchingHandler`
6. [ ] Implement Super Like feature (quick win)

### Medium-Term (Next Month)
7. [ ] Implement Standouts feature - builds on existing Daily Pick
8. [ ] Add "Who Liked Me" functionality
9. [ ] Standardize service naming conventions
10. [ ] Archive/remove bug investigation tests

---

*Generated from code analysis on 2026-01-10*
