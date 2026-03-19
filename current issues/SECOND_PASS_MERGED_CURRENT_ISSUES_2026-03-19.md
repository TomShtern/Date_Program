---
report_type: second-pass-merged-issues
generated_on: 2026-03-19
source_files:
  - c:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program\current issues\CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
  - c:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program\current issues\CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - c:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program\current issues\CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
  - c:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program\current issues\COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
  - c:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program\current issues\MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
  - c:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program\current issues\STATUS_2026-03-09.md
  - c:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program\current issues\STATUS_2026-03-17_By_claude_sonnet_4.6.md
total_raw_mentions_extracted: 391
total_unique_merged_issues: 307
dedupe_reduction_count: 84
dedupe_reduction_percentage: 21.48%
duplicate-collisions-resolved: 72
---

# SECOND-PASS MERGED CURRENT ISSUES (STRICT DEDUPE)

## 1. Match.block() Missing Validation [INVALID] ❌

- canonical title: Match.block() Missing Validation [INVALID] ❌
- severity: critical
- category: data-integrity
- mention_count_total: 3
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 2, CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 2: HIGH Priority Issues > 2.4 Match.block() Missing Validation [INVALID] ❌
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 8. Match.block() Minimal Validation ✅ VERIFIED
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.7 Match Entity Missing Validation
- conflict/status note: Status conflict: at least one source marks INVALID while others mark VALID/PARTIAL/RESOLVED.
- combined explanations:
  - **File:** `core/model/Match.java` (lines 154-162) **✅ VERIFIED:**   **Issue:** Only validates user involvement, not state transition **Severity:** HIGH ---
  - **File:** `core/model/Match.java` (lines 154-162) **Status:** Confirmed - only checks `involves(userId)`, not state transition **Assessment:** May be intentional for defensive blocking
  - **File:** `core/model/Match.java` (lines 163-172) The `block()` method does not validate whether the transition is valid via `isInvalidTransition()`.
  - It can block from any state, unlike `unmatch()` and `gracefulExit()`.
  - **Verification Note:** [INVALID] ❌ Triple-verification confirms this is intentional defensive programming.
  - Blocking is a safety feature that should be available from any state.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
/** Block - ends the match due to blocking. */
public void block(UUID userId) {
    if (!involves(userId)) {
        throw new IllegalArgumentException("User is not part of this match");
    }
    // Can block even if not active (defensive)
    this.state = MatchState.BLOCKED;
  - **File:** `core/model/Match.java` (lines 154-162)
  - **✅ VERIFIED:**
  - **File:** `core/model/Match.java` (lines 163-172)

## 2. User.markDeleted() Missing touch() ✅ VERIFIED

- canonical title: User.markDeleted() Missing touch() ✅ VERIFIED
- severity: critical
- category: concurrency
- mention_count_total: 2
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 2
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 1. User.markDeleted() Missing touch() ✅ VERIFIED
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 1: CRITICAL ISSUES (Must Fix Before Production) > 1.3 User.markDeleted() Missing touch() Call
- conflict/status note: Status signals: INVALID.
- combined explanations:
  - **File:** `core/model/User.java` (lines 658-660) **✅ VERIFIED:**   **Issue:** Method does NOT call `touch()` to update `updatedAt` timestamp.
  - All other mutating methods (e.g., `setName()`, `ban()`) call `touch()`.
  - **Severity:** CRITICAL (Data Integrity) **Fix:** Add `touch();` before closing brace.
  - **File:** `core/model/User.java` (lines 753-756) **Status:** Confirmed - `markDeleted(Instant)` does not call `touch()` **Fix:** Add `touch();` before closing brace
- combined recommendations:
  - Fix:** Add `touch();` before closing brace.
- evidence quote snippets:
  - java
/** Marks this entity as soft-deleted at the given instant. */
public void markDeleted(Instant deletedAt) {
    this.deletedAt = Objects.requireNonNull(deletedAt, "deletedAt cannot be null");
    // ❌ MISSING: touch();
}
  - **File:** `core/model/User.java` (lines 658-660)
  - **✅ VERIFIED:**
  - **File:** `core/model/User.java` (lines 753-756)

## 3. Match Entity Missing updatedAt Field ✅ VERIFIED

- canonical title: Match Entity Missing updatedAt Field ✅ VERIFIED
- severity: critical
- category: data-integrity
- mention_count_total: 2
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 2
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 2. Match Entity Missing updatedAt Field ✅ VERIFIED
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 1: CRITICAL ISSUES (Must Fix Before Production) > 1.4 Match Entity Missing updatedAt Field
- conflict/status note: Status signals: INVALID.
- combined explanations:
  - **File:** `core/model/Match.java` (lines 45-54) **✅ VERIFIED - Field declarations:**   **Issue:** Match entity has `createdAt` but lacks `updatedAt` tracking that `User` entity has.
  - **Severity:** CRITICAL (Data Integrity) **Fix:** Add `private Instant updatedAt;` field and update in all mutating methods.
  - **File:** `core/model/Match.java` (lines 44-53) **Status:** Confirmed - no `updatedAt` field exists **Fix:** Add `updatedAt` field and update in all mutating methods
- combined recommendations:
  - Fix:** Add `private Instant updatedAt;` field and update in all mutating methods.
  - Fix:** Add `updatedAt` field and update in all mutating methods
- evidence quote snippets:
  - java
private final String id;
private final UUID userA;
private final UUID userB;
private final Instant createdAt;
private MatchState state;
private Instant endedAt;
private UUID endedBy;
  - **File:** `core/model/Match.java` (lines 45-54)
  - **✅ VERIFIED - Field declarations:**
  - **File:** `core/model/Match.java` (lines 44-53)

## 4. User.setLocation() Missing Coordinate Validation

- canonical title: User.setLocation() Missing Coordinate Validation
- severity: critical
- category: data-integrity
- mention_count_total: 2
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 2
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 6. User.setLocation() Missing Validation ✅ VERIFIED
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 1: CRITICAL ISSUES (Must Fix Before Production) > 1.5 User.setLocation() Missing Coordinate Validation
- conflict/status note: Status conflict: at least one source marks INVALID while others mark VALID/PARTIAL/RESOLVED.
- combined explanations:
  - **File:** `core/model/User.java` (lines 494-499) **✅ VERIFIED:**   **Issue:** NO coordinate validation.
  - Accepts invalid values: - Out of range: `lat > 90`, `lat < -90`, `lon > 180`, `lon < -180` - Non-finite: `Double.NaN`, `Double.POSITIVE_INFINITY` - Edge case: `(0.0, 0.0)` (Null Island) **Severity:** CRITICAL (Data Quality) **Fix:** Add validation:   ---
  - **File:** `core/model/User.java` (lines 545-550) **Status:** Confirmed - no coordinate range validation **Fix:** Add validation: `if (lat < -90 || lat > 90 || lon < -180 || lon > 180) throw ...`
- combined recommendations:
  - Fix:** Add validation:
  - Fix:** Add validation: `if (lat < -90 || lat > 90 || lon < -180 || lon > 180) throw ...`
- evidence quote snippets:
  - java
public void setLocation(double lat, double lon) {
    this.lat = lat;
    this.lon = lon;
    this.hasLocationSet = true;
    touch();
}
  - java
if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
    throw new IllegalArgumentException("Invalid coordinates");
}
if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
    throw new IllegalArgumentException("Coordinates must be finite");
}
  - **File:** `core/model/User.java` (lines 494-499)
  - **✅ VERIFIED:**
  - **File:** `core/model/User.java` (lines 545-550)

## 5. ProfileService Legacy Achievement Service ✅ VERIFIED

- canonical title: ProfileService Legacy Achievement Service ✅ VERIFIED
- severity: critical
- category: architecture
- mention_count_total: 2
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 2
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 9. ProfileService Legacy Achievement Service ✅ VERIFIED
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.46 ProfileService Legacy Achievement Service Factory
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/profile/ProfileService.java` (Lines 155-169) **Issue:** Creates new `DefaultAchievementService` instances for achievement methods **Severity:** MEDIUM (Architecture) ---
  - **File:** `core/profile/ProfileService.java` (lines 169-173) **Status:** Confirmed - `legacyAchievementService()` creates new instances each call **Fix:** Inject `AchievementService` as dependency
- combined recommendations:
  - Fix:** Inject `AchievementService` as dependency
- evidence quote snippets:
  - **File:** `core/profile/ProfileService.java` (lines 169-173)

## 6. Internationalization: Basic i18n but limited locale support

- canonical title: Internationalization: Basic i18n but limited locale support
- severity: critical
- category: feature-gap
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 11. Identified Gaps and Incomplete Features > UI/UX Gaps
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Internationalization**: Basic i18n but limited locale support - **Offline Mode**: No offline capability for critical features
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 7. API Authentication Bypass Vulnerability

- canonical title: API Authentication Bypass Vulnerability
- severity: critical
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 1: CRITICAL ISSUES (Must Fix Before Production) > 1.1 API Authentication Bypass Vulnerability
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `app/api/RestApiServer.java` (lines 823-834, 688-704) **✅ VERIFIED - `resolveActingUserId()` (lines 823-834):**   **✅ VERIFIED - `sendMessage()` endpoint (lines 688-704):**   **Impact:** API clients can send messages as ANY user by omitting the `X-User-Id` header.
  - Authentication is optional.
  - **Severity:** CRITICAL (Security) **Fix:** Make `X-User-Id` header REQUIRED for all mutation endpoints.
  - Add `requireActingUserId()` method that throws error if not present.
- combined recommendations:
  - Fix:** Make `X-User-Id` header REQUIRED for all mutation endpoints. Add `requireActingUserId()` method that throws error if not present.
- evidence quote snippets:
  - java
private Optional<UUID> resolveActingUserId(Context ctx) {
    String rawUserId = Optional.ofNullable(ctx.header(HEADER_ACTING_USER_ID))
            .filter(value -> !value.isBlank())
            .orElseGet(() -> Optional.ofNullable(ctx.queryParam(QUERY_ACTING_USER_ID))
                    .filter(value -> !value.isBlank())
                    .orElse(null));
    if (rawUserId == null) {
  - java
private void sendMessage(Context ctx) {
    String conversationId = ctx.pathParam(PATH_CONVERSATION_ID);
    SendMessageRequest request = ctx.bodyAsClass(SendMessageRequest.class);

    if (request.senderId() == null || request.content() == null) {
        throw new IllegalArgumentException("senderId and content are required");
    }
  - **File:** `app/api/RestApiServer.java` (lines 823-834, 688-704)
  - **✅ VERIFIED - `resolveActingUserId()` (lines 823-834):**
  - **✅ VERIFIED - `sendMessage()` endpoint (lines 688-704):**

## 8. SQL Injection Risk in Migration Runner

- canonical title: SQL Injection Risk in Migration Runner
- severity: critical
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 1: CRITICAL ISSUES (Must Fix Before Production) > 1.2 SQL Injection Risk in Migration Runner
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `storage/schema/MigrationRunner.java` (lines 153-162) **✅ VERIFIED:**   **Issue:** Uses string concatenation (`"WHERE version = " + version`) instead of parameterized query.
  - While `version` is an `int` parameter (limiting exploit potential), this violates secure coding practices.
  - **Severity:** CRITICAL (Security) **Fix:** Use parameterized query: `prepareStatement("SELECT COUNT(*) FROM schema_version WHERE version = ?")` ---
- combined recommendations:
  - Fix:** Use parameterized query: `prepareStatement("SELECT COUNT(*) FROM schema_version WHERE version = ?")`
- evidence quote snippets:
  - java
static boolean isVersionApplied(Statement stmt, int version) throws SQLException {
    try (ResultSet rs = stmt.executeQuery(
        "SELECT COUNT(*) FROM schema_version WHERE version = " + version)) {  // ← String concatenation
        return rs.next() && rs.getInt(1) > 0;
    } catch (SQLException e) {
        if (isMissingTable(e)) {
            return false;
  - **File:** `storage/schema/MigrationRunner.java` (lines 153-162)
  - **✅ VERIFIED:**

## 9. Missing Tests for Critical Utilities

- canonical title: Missing Tests for Critical Utilities
- severity: critical
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.21 Missing Tests for Critical Utilities
- conflict/status note: Status signals: INVALID.
- combined explanations:
  - **Files:** - `core/matching/InterestMatcher.java` (~126 lines) - No tests ❌ - `core/profile/ProfileCompletionSupport.java` (~330 lines) - No tests ❌ - `core/profile/SanitizerUtils.java` (security-critical) - No tests ❌ **Severity:** HIGH (Quality) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - - `core/matching/InterestMatcher.java` (~126 lines) - No tests ❌
  - - `core/profile/ProfileCompletionSupport.java` (~330 lines) - No tests ❌

## 10. Test Coverage Gaps for Critical Paths

- canonical title: Test Coverage Gaps for Critical Paths
- severity: critical
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.48 Test Coverage Gaps for Critical Paths
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **Issues:** - `JdbiMatchmakingStorage` atomic transition tests incomplete - `MigrationRunner` V3 migration not tested - REST API error handler edge cases **Severity:** MEDIUM (Quality) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 11. DefaultDailyPickService Non-Deterministic Fallback ✅ VERIFIED

- canonical title: DefaultDailyPickService Non-Deterministic Fallback ✅ VERIFIED
- severity: critical
- category: performance
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 3. DefaultDailyPickService Non-Deterministic Fallback ✅ VERIFIED
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/matching/DefaultDailyPickService.java` (lines 66-88) **Status:** Confirmed - generates new random pick on cache miss **Fix:** Return cached pick or empty Optional, don't generate new random
- combined recommendations:
  - Fix:** Return cached pick or empty Optional, don't generate new random
- evidence quote snippets:
  - **File:** `core/matching/DefaultDailyPickService.java` (lines 66-88)

## 12. TrustSafetyService Auto-Ban Single-Instance Only ✅ VERIFIED

- canonical title: TrustSafetyService Auto-Ban Single-Instance Only ✅ VERIFIED
- severity: critical
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 4. TrustSafetyService Auto-Ban Single-Instance Only ✅ VERIFIED
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/matching/TrustSafetyService.java` (lines 224-247) **Status:** Confirmed - `synchronized(this)` only protects single instance **Fix:** Use database-level atomic operations for multi-instance deployment
- combined recommendations:
  - Fix:** Use database-level atomic operations for multi-instance deployment
- evidence quote snippets:
  - **File:** `core/matching/TrustSafetyService.java` (lines 224-247)

## 13. RestApiServer Authentication Bypass ✅ VERIFIED

- canonical title: RestApiServer Authentication Bypass ✅ VERIFIED
- severity: critical
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 5. RestApiServer Authentication Bypass ✅ VERIFIED
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `app/api/RestApiServer.java` (lines 726-737, 622-642) **Status:** Confirmed - `X-User-Id` header optional, validation bypassed when missing **Fix:** Make `X-User-Id` header REQUIRED for all mutation endpoints
- combined recommendations:
  - Fix:** Make `X-User-Id` header REQUIRED for all mutation endpoints
- evidence quote snippets:
  - **File:** `app/api/RestApiServer.java` (lines 726-737, 622-642)

## 14. ChatViewModel Inconsistent Async Pattern ✅ VERIFIED

- canonical title: ChatViewModel Inconsistent Async Pattern ✅ VERIFIED
- severity: critical
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 7. ChatViewModel Inconsistent Async Pattern ✅ VERIFIED
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `ui/viewmodel/ChatViewModel.java` (line 53, lines 858-864) **Status:** Confirmed - doesn't extend `BaseViewModel`, creates own scope **Fix:** Extend `BaseViewModel` or document rationale for separate pattern
- combined recommendations:
  - Fix:** Extend `BaseViewModel` or document rationale for separate pattern
- evidence quote snippets:
  - **File:** `ui/viewmodel/ChatViewModel.java` (line 53, lines 858-864)

## 15. ActivityMetricsService Lock Striping ✅ VERIFIED

- canonical title: ActivityMetricsService Lock Striping ✅ VERIFIED
- severity: critical
- category: general
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 10. ActivityMetricsService Lock Striping ✅ VERIFIED
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/metrics/ActivityMetricsService.java` (line 23) **Status:** Confirmed - 256 lock stripes **Assessment:** May need increase for millions of users
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `core/metrics/ActivityMetricsService.java` (line 23)

## 16. RelationshipWorkflowPolicy Terminal States [INVALID] ❌

- canonical title: RelationshipWorkflowPolicy Terminal States [INVALID] ❌
- severity: critical
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 11. RelationshipWorkflowPolicy Terminal States [INVALID] ❌
- conflict/status note: Status conflict: at least one source marks INVALID while others mark VALID/PARTIAL/RESOLVED.
- combined explanations:
  - **File:** `core/workflow/RelationshipWorkflowPolicy.java` (lines 17-20) **Status:** Confirmed - `PENDING`, `FRIEND_ZONE_REQUESTED` not in transition map **Assessment:** [INVALID] ❌ Triple-verification confirms this is intentional and logically sound for the current feature set.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `core/workflow/RelationshipWorkflowPolicy.java` (lines 17-20)

## 17. RestApiServer Deliberate Use-Case Bypass ✅ VERIFIED

- canonical title: RestApiServer Deliberate Use-Case Bypass ✅ VERIFIED
- severity: critical
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 12. RestApiServer Deliberate Use-Case Bypass ✅ VERIFIED
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/api/RestApiServer.java` (lines 290-300, 304-332) **Status:** Confirmed - 5 "Deliberate exception" comments for read-only routes **Assessment:** Architectural decision awaiting use-case layer expansion
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `app/api/RestApiServer.java` (lines 290-300, 304-332)

## 18. ProfileUseCases Transaction Boundary ✅ VERIFIED

- canonical title: ProfileUseCases Transaction Boundary ✅ VERIFIED
- severity: critical
- category: architecture
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 13. ProfileUseCases Transaction Boundary ✅ VERIFIED
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/usecase/profile/ProfileUseCases.java` (lines 188-214) **Status:** Confirmed - separate try-catch for save vs achievements **Fix:** Wrap in single transaction or add compensation logic
- combined recommendations:
  - Fix:** Wrap in single transaction or add compensation logic
- evidence quote snippets:
  - **File:** `app/usecase/profile/ProfileUseCases.java` (lines 188-214)

## 19. Missing Test Files ✅ VERIFIED

- canonical title: Missing Test Files ✅ VERIFIED
- severity: critical
- category: testing
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 14. Missing Test Files ✅ VERIFIED
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **Files:** `InterestMatcher.java` (~126 lines), `ProfileCompletionSupport.java` (~330 lines), `SanitizerUtils.java` **Status:** Confirmed - no test files exist for these critical utilities **Fix:** Add unit tests for these classes
- combined recommendations:
  - Fix:** Add unit tests for these classes
- evidence quote snippets:
  - **Files:** `InterestMatcher.java` (~126 lines), `ProfileCompletionSupport.java` (~330 lines), `SanitizerUtils.java`

## 20. InteractionStorage In-Memory Pagination ✅ VERIFIED

- canonical title: InteractionStorage In-Memory Pagination ✅ VERIFIED
- severity: critical
- category: general
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > APPENDIX B: VERIFIED CRITICAL ISSUES (Actionable) > 15. InteractionStorage In-Memory Pagination ✅ VERIFIED
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/storage/InteractionStorage.java` (lines 145-163) **Status:** Confirmed - `getAllMatchesFor()` then `subList()` **Fix:** Override in `JdbiMatchmakingStorage` with SQL LIMIT/OFFSET ---
- combined recommendations:
  - Fix:** Override in `JdbiMatchmakingStorage` with SQL LIMIT/OFFSET
- evidence quote snippets:
  - **File:** `core/storage/InteractionStorage.java` (lines 145-163)

## 21. Schema Inconsistency - Missing daily_picks Table in V1

- canonical title: Schema Inconsistency - Missing daily_picks Table in V1
- severity: critical
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 1: CRITICAL Issues > 1.1 Schema Inconsistency - Missing daily_picks Table in V1
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `src/main/java/datingapp/storage/schema/SchemaInitializer.java` The SchemaInitializer (V1 baseline) does NOT create the `daily_picks` table - only `daily_pick_views`.
  - However, MigrationRunner V2 (line 129-139) DOES create this table with FK constraints.
  - The comment in MigrationRunner line 70 states: "no-op on fresh databases where V1 already includes it" - this comment is **INCORRECT/MISLEADING** as V1 does NOT include it.
  - For fresh database installations, the migrations run in order (V1 then V2), so the table IS created.
  - The issue is the misleading comment that could confuse future developers.
  - **Note:** There IS a test (`SchemaInitializerTest.java` line 161) that expects `daily_picks` to exist after running migrations - this test passes because V2 runs and creates the table.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - However, MigrationRunner V2 (line 129-139) DOES create this table with FK constraints. The comment in MigrationRunner line 70 states: "no-op on fresh databases where V1 already includes it" - this comment is **INCORRECT/MISLEADING** as V1 does NOT include it.
  - **Note:** There IS a test (`SchemaInitializerTest.java` line 161) that expects `daily_picks` to exist after running migrations - this test passes because V2 runs and creates the table.

## 22. Entity touch() Calls Missing on State Transitions

- canonical title: Entity touch() Calls Missing on State Transitions
- severity: critical
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 1: CRITICAL Issues > 1.2 Entity touch() Calls Missing on State Transitions
- conflict/status note: Status signals: INVALID.
- combined explanations:
  - **File:** `core/model/User.java` **`markDeleted(Instant)` method (lines 819-821):**   **File:** `core/model/Match.java` Match entity has **NO `updatedAt` field** despite multiple state-changing methods: - `unmatch()` (line 149) - `block()` (line 163) - `transitionToFriends()` (line 180) - `gracefulExit()` (line 199) - `revertToActive()` (line 211) **Impact:** Audit trail gaps, incorrect "last modified" reporting, GDPR purge logic may fail.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
public void markDeleted(Instant deletedAt) {
    this.deletedAt = Objects.requireNonNull(deletedAt, "deletedAt cannot be null");
    // ❌ MISSING: touch();
}
  - **`markDeleted(Instant)` method (lines 819-821):**
  - - `unmatch()` (line 149)
  - - `block()` (line 163)
  - - `transitionToFriends()` (line 180)
  - - `gracefulExit()` (line 199)
  - - `revertToActive()` (line 211)

## 23. Daily Pick Service Non-Deterministic Fallback

- canonical title: Daily Pick Service Non-Deterministic Fallback
- severity: critical
- category: performance
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 1: CRITICAL Issues > 1.3 Daily Pick Service Non-Deterministic Fallback
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/matching/DefaultDailyPickService.java` (lines 66-127) The service has two fallback mechanisms that can generate different random picks if: - Cache miss occurs - First candidate not found in list This breaks the "once per day" guarantee.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `core/matching/DefaultDailyPickService.java` (lines 66-127)

## 24. Trust Safety Auto-Ban Race Condition

- canonical title: Trust Safety Auto-Ban Race Condition
- severity: critical
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 1: CRITICAL Issues > 1.4 Trust Safety Auto-Ban Race Condition
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/matching/TrustSafetyService.java` (lines 224-247) The `synchronized(this)` block only protects against concurrent calls on the same instance.
  - In a clustered deployment, multiple nodes could trigger the ban simultaneously.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `core/matching/TrustSafetyService.java` (lines 224-247)

## 25. REST API Optional Authentication

- canonical title: REST API Optional Authentication
- severity: critical
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 1: CRITICAL Issues > 1.5 REST API Optional Authentication
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/api/RestApiServer.java` (lines 884-894) The `resolveActingUserId()` method is OPTIONAL - it returns empty if no header is provided:   However, there IS a `requireActingUserId()` method (line 896) that throws if no user is provided.
  - **Current state:** Some endpoints use optional authentication (`resolveActingUserId`) while others require it (`requireActingUserId`).
  - This inconsistency could lead to security issues if developers pick the wrong method.
  - **Recommendation:** Audit all endpoints to ensure they use appropriate authentication level.
- combined recommendations:
  - Recommendation:** Audit all endpoints to ensure they use appropriate authentication level.
- evidence quote snippets:
  - java
if (rawUserId == null) {
    return Optional.empty();  // ← Optional - not required!
}
  - **File:** `app/api/RestApiServer.java` (lines 884-894)
  - However, there IS a `requireActingUserId()` method (line 896) that throws if no user is provided.

## 26. Conversation Hard-Delete Instead of Soft-Delete

- canonical title: Conversation Hard-Delete Instead of Soft-Delete
- severity: critical
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 1: CRITICAL Issues > 1.6 Conversation Hard-Delete Instead of Soft-Delete
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java` (lines 96-98) Uses hard DELETE while all other entities (users, likes, matches) use soft-delete: - SchemaInitializer line 274 defines `deleted_at TIMESTAMP` - But `messagingDao.deleteConversation()` uses hard DELETE (line 350-351) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java` (lines 96-98)
  - - SchemaInitializer line 274 defines `deleted_at TIMESTAMP`
  - - But `messagingDao.deleteConversation()` uses hard DELETE (line 350-351)

## 27. Blocks/Reports Tables Missing Soft-Delete Filtering

- canonical title: Blocks/Reports Tables Missing Soft-Delete Filtering
- severity: critical
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 1: CRITICAL Issues > 1.7 Blocks/Reports Tables Missing Soft-Delete Filtering
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **Files:** `JdbiTrustSafetyStorage.java`, `SchemaInitializer.java` The `blocks` and `reports` tables have `deleted_at` columns but: - No queries filter on `deleted_at IS NULL` - No soft-delete is performed when deleting blocks/reports - Inconsistent with all other tables ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 28. Profile Note Hard-Delete

- canonical title: Profile Note Hard-Delete
- severity: critical
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 1: CRITICAL Issues > 1.8 Profile Note Hard-Delete
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` (lines 514-518) Uses hard DELETE instead of soft-delete, inconsistent with other tables.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` (lines 514-518)

## 29. Summary: Critical business logic thresholds were centralized into `AppConfig.java`, but this historical note about `AppConfig.defaults()` is stale.

- canonical title: Summary: Critical business logic thresholds were centralized into `AppConfig.java`, but this historical note about `AppConfig.defaults()` is stale.
- severity: critical
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.5 Configuration & Constants > FI-CONS-010: Fragmented Configuration Constants / Inconsistent Sourcing
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: Critical business logic thresholds were centralized into `AppConfig.java`, but this historical note about `AppConfig.defaults()` is stale.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 30. Status: VALID 🟢

- canonical title: Status: VALID 🟢
- severity: high
- category: general
- mention_count_total: 5
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 5
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-001: Layer boundaries are porous and service boundaries are bypassed
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-002: Global mutable singleton state is pervasive
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-004: Large multi-responsibility units and broad interfaces create high change blast radius
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.2 Code Architecture & Organization > FI-CONS-002: UI Controller Bloat - ProfileController
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.6 Storage & JDBI Optimization > FI-CONS-013: Over-engineered Stateless Algorithms
- conflict/status note: Status conflict: at least one source marks INVALID while others mark VALID/PARTIAL/RESOLVED.
- combined explanations:
  - *   **Status**: INVALID 🔴
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 31. Severity: High

- canonical title: Severity: High
- severity: high
- category: general
- mention_count_total: 3
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 3
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-001: Layer boundaries are porous and service boundaries are bypassed
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-002: Global mutable singleton state is pervasive
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-003: Several services are partially constructible and rely on runtime null-check mode switching
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Severity**: High
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 32. Optional Dependencies Not Null-Checked

- canonical title: Optional Dependencies Not Null-Checked
- severity: high
- category: ui-ux
- mention_count_total: 2
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1, CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 2: HIGH Priority Issues > 2.2 Optional Dependencies Not Null-Checked
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.2 Optional Dependencies Not Null-Checked
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/matching/MatchingService.java` (lines 141-160) **✅ VERIFIED:**   **Issue:** Null checks exist but optional dependencies can cause silent failures **Severity:** HIGH ---
  - **File:** `core/matching/MatchingService.java` (line 33-36) Optional dependencies (`activityMetricsService`, `undoService`, `dailyService`, `candidateFinder`) are declared without null checks, leading to potential NPEs when services are not wired.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
public Optional<Match> recordLike(Like like) {
    Objects.requireNonNull(like, LIKE_REQUIRED);
    InteractionStorage.LikeMatchWriteResult writeResult = interactionStorage.saveLikeAndMaybeCreateMatch(like);
    if (activityMetricsService != null && writeResult.createdMatch().isPresent()) {
        activityMetricsService.recordSwipe(like.whoLikes(), like.direction(), true);
    }
    return writeResult.createdMatch();
  - **File:** `core/matching/MatchingService.java` (lines 141-160)
  - **✅ VERIFIED:**
  - **File:** `core/matching/MatchingService.java` (line 33-36)

## 33. ProfileService Achievement Service Instantiated Per-Call

- canonical title: ProfileService Achievement Service Instantiated Per-Call
- severity: high
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1, CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.13 ProfileService Achievement Service Created on Every Call
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.8 ProfileService Achievement Service Instantiated Per-Call
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/profile/ProfileService.java` (lines 169-173) **✅ VERIFIED:**   **Impact:** Inefficient object creation; no shared state between calls **Severity:** HIGH ---
  - **File:** `core/profile/ProfileService.java` (lines 185-188) The `legacyAchievementService()` method creates a new `DefaultAchievementService` instance on every call.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
private DefaultAchievementService legacyAchievementService() {
    return new DefaultAchievementService(
            config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage, this);
}
  - **File:** `core/profile/ProfileService.java` (lines 169-173)
  - **✅ VERIFIED:**
  - **File:** `core/profile/ProfileService.java` (lines 185-188)

## 34. N+1 Query Pattern in User Storage

- canonical title: N+1 Query Pattern in User Storage
- severity: high
- category: performance
- mention_count_total: 2
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1, CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 2: HIGH Priority Issues > 2.10 N+1 Query Pattern in User Storage
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.19 N+1 Query Pattern in User Storage
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `storage/jdbi/JdbiUserStorage.java` **Issue:** Loading normalized profile data triggers multiple queries per user **Severity:** HIGH (Performance) ---
  - **File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` (lines 140-158) For each user, separate queries are made for photos, interests, gender preferences, and dealbreakers - resulting in N+1 queries when loading multiple users.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` (lines 140-158)

## 35. MatchingUseCases: High-level matching workflows

- canonical title: MatchingUseCases: High-level matching workflows
- severity: high
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 8. Use Case Layer > Business Logic Organization (`app/usecase/`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **MatchingUseCases**: High-level matching workflows
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 36. Status: PARTIAL 🟡

- canonical title: Status: PARTIAL 🟡
- severity: high
- category: general
- mention_count_total: 2
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 2
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-003: Several services are partially constructible and rely on runtime null-check mode switching
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.5 Configuration & Constants > FI-CONS-010: Fragmented Configuration Constants / Inconsistent Sourcing
- conflict/status note: Status signals: PARTIAL.
- combined explanations:
  - *   **Status**: PARTIAL 🟡
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 37. Storage Interface Default Implementations Mask Incomplete Features

- canonical title: Storage Interface Default Implementations Mask Incomplete Features
- severity: high
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.1 Storage Interface Default Implementations Mask Incomplete Features
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/storage/InteractionStorage.java` (lines 183, 188-191) **✅ VERIFIED:**   **Impact:** Atomic relationship transitions may not work in production unless `JdbiMatchmakingStorage` overrides these **Severity:** HIGH ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
default int purgeDeletedBefore(Instant threshold) {
    return 0;  // No-op
}

default boolean supportsAtomicRelationshipTransitions() {
    return false;  // Default: no atomic support
}
  - **File:** `core/storage/InteractionStorage.java` (lines 183, 188-191)
  - **✅ VERIFIED:**

## 38. AchievementService Falls Back to ProfileService

- canonical title: AchievementService Falls Back to ProfileService
- severity: high
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.3 AchievementService Falls Back to ProfileService
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/usecase/profile/ProfileUseCases.java` (lines 518-526) **✅ VERIFIED:**   **Impact:** Duplicate achievement logic; potential inconsistency **Severity:** HIGH ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
private List<UserAchievement> unlockAchievements(UUID userId) {
    if (achievementService != null) {
        return achievementService.checkAndUnlock(userId);
    }
    if (profileService != null) {
        return profileService.checkAndUnlock(userId);  // Fallback
    }
  - **File:** `app/usecase/profile/ProfileUseCases.java` (lines 518-526)
  - **✅ VERIFIED:**

## 39. Presence Feature Flag Undocumented

- canonical title: Presence Feature Flag Undocumented
- severity: high
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.4 Presence Feature Flag Undocumented
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `ui/viewmodel/ViewModelFactory.java` **Issue:** Feature controlled by system property `datingapp.ui.presence.enabled` without documentation **Severity:** HIGH ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 40. CommunicationStorage Nullable in TrustSafetyService

- canonical title: CommunicationStorage Nullable in TrustSafetyService
- severity: high
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.5 CommunicationStorage Nullable in TrustSafetyService
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - **File:** `core/matching/TrustSafetyService.java` (line 33, lines 69-79) **✅ VERIFIED:**   **Impact:** Silent failures in `updateMatchStateForBlock()` when null **Severity:** HIGH ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
private final CommunicationStorage communicationStorage;  // nullable (line 33)

private TrustSafetyService(Builder builder) {
    // ...
    this.communicationStorage = resolvedBuilder.communicationStorage;  // NO null check (line 75)
    // ...
}
  - **File:** `core/matching/TrustSafetyService.java` (line 33, lines 69-79)
  - **✅ VERIFIED:**
  - private final CommunicationStorage communicationStorage;  // nullable (line 33)
  - this.communicationStorage = resolvedBuilder.communicationStorage;  // NO null check (line 75)

## 41. User Entity Deprecated Methods Without Removal Plan

- canonical title: User Entity Deprecated Methods Without Removal Plan
- severity: high
- category: architecture
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.6 User Entity Deprecated Methods Without Removal Plan
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/model/User.java` (lines 447-452, 572-577, 604-609) **✅ VERIFIED - 3 deprecated methods:**   **Severity:** HIGH (Technical Debt) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
@Deprecated(since = "2026-03", forRemoval = false)
public Optional<Integer> getAge() { ... }

@Deprecated(since = "2026-03", forRemoval = false)
public void setMaxDistanceKm(int maxDistanceKm) { ... }

@Deprecated(since = "2026-03", forRemoval = false)
  - **File:** `core/model/User.java` (lines 447-452, 572-577, 604-609)
  - **✅ VERIFIED - 3 deprecated methods:**

## 42. Email Validation Doesn't Support International Domains

- canonical title: Email Validation Doesn't Support International Domains
- severity: high
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.9 Email Validation Doesn't Support International Domains
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `core/profile/ValidationService.java` (line 33) **✅ VERIFIED:**   **Impact:** Valid emails like `user@пример.рф` are rejected **Severity:** HIGH ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
  - **File:** `core/profile/ValidationService.java` (line 33)
  - **✅ VERIFIED:**

## 43. Connection Service Message Sanitization

- canonical title: Connection Service Message Sanitization
- severity: high
- category: general
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.10 Connection Service Message Sanitization
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/connection/ConnectionService.java` (line 101) **✅ VERIFIED:**   **Issue:** OWASP sanitizer strips ALL HTML - users can't send formatted text **Severity:** HIGH ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
content = SanitizerUtils.sanitize(content);  // Line 101
  - **File:** `core/connection/ConnectionService.java` (line 101)
  - **✅ VERIFIED:**

## 44. Activity Metrics Lock Striping Hash Collisions

- canonical title: Activity Metrics Lock Striping Hash Collisions
- severity: high
- category: performance
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.11 Activity Metrics Lock Striping Hash Collisions
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/metrics/ActivityMetricsService.java` (line 23) **✅ VERIFIED:**   **Impact:** Performance degradation under high load (millions of users) **Severity:** HIGH ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
private static final int LOCK_STRIPE_COUNT = 256;
  - **File:** `core/metrics/ActivityMetricsService.java` (line 23)
  - **✅ VERIFIED:**

## 45. Relationship Workflow Policy Incomplete State Machine [INVALID] ❌

- canonical title: Relationship Workflow Policy Incomplete State Machine [INVALID] ❌
- severity: high
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.12 Relationship Workflow Policy Incomplete State Machine [INVALID] ❌
- conflict/status note: Status conflict: at least one source marks INVALID while others mark VALID/PARTIAL/RESOLVED.
- combined explanations:
  - **File:** `core/workflow/RelationshipWorkflowPolicy.java` (lines 17-20) **✅ VERIFIED:**   **Missing:** `PENDING`, `FRIEND_ZONE_REQUESTED` states not represented **Severity:** HIGH **Verification Note:** [INVALID] ❌ Triple-verification confirms the policy is logically sound for the current implementation.
  - `PENDING` states are not used in the current version of the app.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
private static final Map<MatchState, Set<MatchState>> ALLOWED_TRANSITIONS = Map.of(
        MatchState.ACTIVE,
                Set.of(MatchState.FRIENDS, MatchState.UNMATCHED, MatchState.GRACEFUL_EXIT, MatchState.BLOCKED),
        MatchState.FRIENDS, Set.of(MatchState.UNMATCHED, MatchState.GRACEFUL_EXIT, MatchState.BLOCKED));
  - **File:** `core/workflow/RelationshipWorkflowPolicy.java` (lines 17-20)
  - **✅ VERIFIED:**

## 46. API Endpoints Bypass Use-Case Layer

- canonical title: API Endpoints Bypass Use-Case Layer
- severity: high
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.13 API Endpoints Bypass Use-Case Layer
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/api/RestApiServer.java` (lines 290-300, 304-332) **✅ VERIFIED - 5 "Deliberate exception" comments:**   **Impact:** Architecture inconsistency; business logic duplication risk **Severity:** HIGH (Architecture) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
// Line 291: getCandidates
// Deliberate exception: read-only candidate projection route.

// Line 305: getMatches
// Deliberate exception: this route needs paginated match reads...
  - **File:** `app/api/RestApiServer.java` (lines 290-300, 304-332)
  - **✅ VERIFIED - 5 "Deliberate exception" comments:**

## 47. Missing Error Handling in CLI Handlers

- canonical title: Missing Error Handling in CLI Handlers
- severity: high
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.14 Missing Error Handling in CLI Handlers
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/cli/MatchingHandler.java` **Issue:** `logTransitionResult()` only logs on failure but doesn't inform users WHY **Severity:** HIGH (UX) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 48. Duplicate Event Publishing

- canonical title: Duplicate Event Publishing
- severity: high
- category: general
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.15 Duplicate Event Publishing
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/usecase/matching/MatchingUseCases.java` (lines 251, 255-256, 348, 351-352) **✅ VERIFIED:** Each method (`processSwipe`, `recordLike`) publishes events once.
  - **Correction:** Events are NOT published twice within the same method.
  - Each method has its own event publishing logic.
  - **Severity:** HIGH - Downgraded from initial report (no double-publishing within single method) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `app/usecase/matching/MatchingUseCases.java` (lines 251, 255-256, 348, 351-352)
  - **✅ VERIFIED:** Each method (`processSwipe`, `recordLike`) publishes events once.

## 49. Transaction Boundary Issues

- canonical title: Transaction Boundary Issues
- severity: high
- category: architecture
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.16 Transaction Boundary Issues
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/usecase/profile/ProfileUseCases.java` (lines 188-214) **✅ VERIFIED:**   **Impact:** User saved but achievements/notifications lost on failure **Severity:** HIGH ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
try {
    userStorage.save(user);  // Line 203: DB save
} catch (Exception e) {
    return UseCaseResult.failure(...);
}

List<UserAchievement> newAchievements = List.of();
  - **File:** `app/usecase/profile/ProfileUseCases.java` (lines 188-214)
  - **✅ VERIFIED:**

## 50. ChatViewModel Listener Cleanup Race Condition

- canonical title: ChatViewModel Listener Cleanup Race Condition
- severity: high
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.17 ChatViewModel Listener Cleanup Race Condition
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `ui/viewmodel/ChatViewModel.java` (lines 93, 186, 198) **✅ VERIFIED:**   **Issue:** Listener may fire while being removed in `dispose()` **Severity:** HIGH ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
// Line 93: Listener reference
private final javafx.beans.value.ChangeListener<ConversationPreview> selectionListener;

// Line 186: Listener added
selectedConversation.addListener(selectionListener);

// Line 198: Listener removed in dispose
  - **File:** `ui/viewmodel/ChatViewModel.java` (lines 93, 186, 198)
  - **✅ VERIFIED:**

## 51. DashboardController Achievement Popup Silent Failure

- canonical title: DashboardController Achievement Popup Silent Failure
- severity: high
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.18 DashboardController Achievement Popup Silent Failure
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `ui/screen/DashboardController.java` (lines 415-420) **✅ VERIFIED:**   **Severity:** HIGH (UX) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
try {
    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/achievement_popup.fxml"));
    StackPane popupRoot = loader.load();
    // ...
} catch (IOException e) {
    logger.error("Failed to load achievement popup for {}", achievement.getDisplayName(), e);
    // No user feedback!
  - **File:** `ui/screen/DashboardController.java` (lines 415-420)
  - **✅ VERIFIED:**

## 52. V3 Migration Data Loss

- canonical title: V3 Migration Data Loss
- severity: high
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.20 V3 Migration Data Loss
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `storage/schema/MigrationRunner.java` (lines 121-130) **✅ VERIFIED:**   **Impact:** Users with data only in legacy columns lose that data **Severity:** HIGH (Data Integrity) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
private static void applyV3(Statement stmt) throws SQLException {
    stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS photo_urls");
    stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS interests");
    stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS interested_in");
    stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_smoking");
    stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_drinking");
    stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_wants...
  - **File:** `storage/schema/MigrationRunner.java` (lines 121-130)
  - **✅ VERIFIED:**

## 53. In-Memory Pagination in Storage Interfaces

- canonical title: In-Memory Pagination in Storage Interfaces
- severity: high
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.22 In-Memory Pagination in Storage Interfaces
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/storage/InteractionStorage.java` (lines 145-163) **✅ VERIFIED:**   **Impact:** OOM risk for users with thousands of matches **Severity:** HIGH (Performance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
default PageData<Match> getPageOfMatchesFor(UUID userId, int offset, int limit) {
    List<Match> all = getAllMatchesFor(userId);  // Loads ALL matches
    int total = all.size();
    // ...
    List<Match> page = all.subList(offset, end);  // In-memory slicing
    return new PageData<>(page, total, offset, limit);
}
  - **File:** `core/storage/InteractionStorage.java` (lines 145-163)
  - **✅ VERIFIED:**

## 54. REST API Rate Limiter In-Memory Only

- canonical title: REST API Rate Limiter In-Memory Only
- severity: high
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 2: HIGH SEVERITY ISSUES > 2.23 REST API Rate Limiter In-Memory Only
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/api/RestApiServer.java` (lines 1035-1053) **✅ VERIFIED:**   **Severity:** HIGH (Security) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
private static final class LocalRateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    // In-memory only - resets on restart
}
  - **File:** `app/api/RestApiServer.java` (lines 1035-1053)
  - **✅ VERIFIED:**

## 55. Unbounded Query in findCandidates

- canonical title: Unbounded Query in findCandidates
- severity: high
- category: performance
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 2: HIGH Priority Issues > 2.1 Unbounded Query in findCandidates
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` (lines 80-132) If `maxDistanceKm >= 50_000`, the bounding-box filter is skipped entirely and ALL active users are returned.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` (lines 80-132)

## 56. User State Transitions Don't Update Timestamps

- canonical title: User State Transitions Don't Update Timestamps
- severity: high
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 2: HIGH Priority Issues > 2.3 User State Transitions Don't Update Timestamps
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/model/User.java` (lines 728-737) The `activate()`, `pause()`, and `ban()` state transitions don't consistently update `updatedAt`.
  - All other setters call `touch()` but these state transitions don't.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `core/model/User.java` (lines 728-737)

## 57. Report Result Message Inconsistency

- canonical title: Report Result Message Inconsistency
- severity: high
- category: general
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 2: HIGH Priority Issues > 2.5 Report Result Message Inconsistency
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/cli/SafetyHandler.java` (lines 235-248) `handleReportResult()` always says "has been blocked" regardless of the `blockUser` parameter passed.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `app/cli/SafetyHandler.java` (lines 235-248)

## 58. Super Like Feature Not Implemented

- canonical title: Super Like Feature Not Implemented
- severity: high
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 2: HIGH Priority Issues > 2.6 Super Like Feature Not Implemented
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `ui/screen/MatchingController.java` (lines 496-497)   Super Like button exists but does nothing special - acts as regular like.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
// For now, acts like a regular like (super like logic to be added later)
viewModel.like();
  - **File:** `ui/screen/MatchingController.java` (lines 496-497)

## 59. Presence Tracking Disabled

- canonical title: Presence Tracking Disabled
- severity: high
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 2: HIGH Priority Issues > 2.7 Presence Tracking Disabled
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `ui/screen/MatchesController.java` (lines 566-567)   Presence is disabled by default via system property.
  - All matches show "Offline" regardless of actual status.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
// FUTURE(presence-tracking): Replace with live status once UserPresenceService is implemented.
// Currently always shows "Offline".
  - **File:** `ui/screen/MatchesController.java` (lines 566-567)

## 60. Location Feature Limited to Israel

- canonical title: Location Feature Limited to Israel
- severity: high
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 2: HIGH Priority Issues > 2.8 Location Feature Limited to Israel
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `ui/screen/ProfileController.java` (lines 1148, 1194) Only Israel is supported for location:   ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
showLocationDialogError(dialogRefs.errorLabel(), newVal.name() + " is coming soon. Please choose Israel for now.");
  - **File:** `ui/screen/ProfileController.java` (lines 1148, 1194)

## 61. Photo URL Limit Not Enforced

- canonical title: Photo URL Limit Not Enforced
- severity: high
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 2: HIGH Priority Issues > 2.9 Photo URL Limit Not Enforced
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/model/User.java` (lines 622-632) `addPhotoUrl()` and `setPhotoUrls()` do not enforce a maximum limit.
  - AppConfig has `maxPhotos` (default 2), but no enforcement in User model.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `core/model/User.java` (lines 622-632)

## 62. Summary: Handlers/viewmodels/API frequently bypass core service boundaries. `ServiceRegistry.java` exposes storage objects directly to upper layers. Application entry points and `RestApiServer.java` inject storages directly and mutate state via `interactionStorage.update(match)`. UI adapters expose storage-level operations to ViewModels.

- canonical title: Summary: Handlers/viewmodels/API frequently bypass core service boundaries. `ServiceRegistry.java` exposes storage objects directly to upper layers. Application entry points and `RestApiServer.java` inject storages directly and mutate state via `interactionStorage.update(match)`. UI adapters expose storage-level operations to ViewModels.
- severity: high
- category: security
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-001: Layer boundaries are porous and service boundaries are bypassed
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: Handlers/viewmodels/API frequently bypass core service boundaries.
  - `ServiceRegistry.java` exposes storage objects directly to upper layers.
  - Application entry points and `RestApiServer.java` inject storages directly and mutate state via `interactionStorage.update(match)`.
  - UI adapters expose storage-level operations to ViewModels.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 63. Recommendation: Enforce strict boundary rules where handlers and UI adapters rely only on service interfaces, not storage.

- canonical title: Recommendation: Enforce strict boundary rules where handlers and UI adapters rely only on service interfaces, not storage.
- severity: high
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-001: Layer boundaries are porous and service boundaries are bypassed
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Recommendation**: Enforce strict boundary rules where handlers and UI adapters rely only on service interfaces, not storage.
- combined recommendations:
  - *   **Recommendation**: Enforce strict boundary rules where handlers and UI adapters rely only on service interfaces, not storage.
- evidence quote snippets:

## 64. Summary: `ApplicationStartup.java`, `AppSession.java`, `AppClock.java`, and `NavigationService.java` are process-global mutable singletons. Multiple entry points initialize the same global startup path.

- canonical title: Summary: `ApplicationStartup.java`, `AppSession.java`, `AppClock.java`, and `NavigationService.java` are process-global mutable singletons. Multiple entry points initialize the same global startup path.
- severity: high
- category: architecture
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-002: Global mutable singleton state is pervasive
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: `ApplicationStartup.java`, `AppSession.java`, `AppClock.java`, and `NavigationService.java` are process-global mutable singletons.
  - Multiple entry points initialize the same global startup path.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 65. Recommendation: Reduce singleton usage or encapsulate global state to avoid hidden coupling and brittle tests.

- canonical title: Recommendation: Reduce singleton usage or encapsulate global state to avoid hidden coupling and brittle tests.
- severity: high
- category: testing
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-002: Global mutable singleton state is pervasive
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Recommendation**: Reduce singleton usage or encapsulate global state to avoid hidden coupling and brittle tests.
- combined recommendations:
  - *   **Recommendation**: Reduce singleton usage or encapsulate global state to avoid hidden coupling and brittle tests.
- evidence quote snippets:

## 66. Summary: Services like `MatchingService` historically left dependencies like `dailyService` and `undoService` as optional nulls, branching behavior at runtime.

- canonical title: Summary: Services like `MatchingService` historically left dependencies like `dailyService` and `undoService` as optional nulls, branching behavior at runtime.
- severity: high
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-003: Several services are partially constructible and rely on runtime null-check mode switching
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: Services like `MatchingService` historically left dependencies like `dailyService` and `undoService` as optional nulls, branching behavior at runtime.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 67. Resolution: Recent refactoring has replaced many multiple constructor overloads with strict Builder patterns and `Objects.requireNonNull`, but some runtime mode switches persist in `MatchingService`.

- canonical title: Resolution: Recent refactoring has replaced many multiple constructor overloads with strict Builder patterns and `Objects.requireNonNull`, but some runtime mode switches persist in `MatchingService`.
- severity: high
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-003: Several services are partially constructible and rely on runtime null-check mode switching
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Resolution**: Recent refactoring has replaced many multiple constructor overloads with strict Builder patterns and `Objects.requireNonNull`, but some runtime mode switches persist in `MatchingService`.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 68. Severity: Medium-High

- canonical title: Severity: Medium-High
- severity: high
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-004: Large multi-responsibility units and broad interfaces create high change blast radius
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Severity**: Medium-High
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 69. Summary: Very large classes with mixed concerns (e.g., `ProfileHandler.java`, `MatchingHandler.java`, `User.java`, `MatchPreferences.java`). Consolidated interfaces cover many subdomains.

- canonical title: Summary: Very large classes with mixed concerns (e.g., `ProfileHandler.java`, `MatchingHandler.java`, `User.java`, `MatchPreferences.java`). Consolidated interfaces cover many subdomains.
- severity: high
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-004: Large multi-responsibility units and broad interfaces create high change blast radius
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: Very large classes with mixed concerns (e.g., `ProfileHandler.java`, `MatchingHandler.java`, `User.java`, `MatchPreferences.java`).
  - Consolidated interfaces cover many subdomains.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 70. Recommendation: Split large classes into smaller, focused single-responsibility services or components.

- canonical title: Recommendation: Split large classes into smaller, focused single-responsibility services or components.
- severity: high
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-004: Large multi-responsibility units and broad interfaces create high change blast radius
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Recommendation**: Split large classes into smaller, focused single-responsibility services or components.
- combined recommendations:
  - *   **Recommendation**: Split large classes into smaller, focused single-responsibility services or components.
- evidence quote snippets:

## 71. Missing Composite Indexes on Matches

- canonical title: Missing Composite Indexes on Matches
- severity: medium
- category: data-integrity
- mention_count_total: 2
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1, CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 4: LOW Priority Issues > 4.2 Missing Composite Indexes
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.18 Missing Composite Indexes on Matches
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/storage/schema/SchemaInitializer.java` (Lines 432-436) **Issue:** No composite index for `(user_a, state, deleted_at)` pattern **Impact:** Inefficient active match lookups **Severity:** MEDIUM (Performance) ---
  - Several queries would benefit from composite indexes that don't exist: - `likes(who_likes, deleted_at)` - `matches(user_a, state, deleted_at)` - `messages(conversation_id, sender_id)` ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 72. Storage Batch Query Default Implementations

- canonical title: Storage Batch Query Default Implementations
- severity: medium
- category: performance
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.1 Storage Batch Query Default Implementations
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/storage/CommunicationStorage.java` (Lines 55-68)   **Severity:** MEDIUM (Performance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
default Map<String, Integer> countMessagesByConversationIds(List<String> conversationIds) {
    Map<String, Integer> counts = new HashMap<>();
    for (String id : conversationIds) {
        counts.put(id, countMessages(id));  // N+1 query
    }
    return counts;
}

## 73. UserStorage.findByIds Default Implementation Inefficient

- canonical title: UserStorage.findByIds Default Implementation Inefficient
- severity: medium
- category: performance
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.2 UserStorage.findByIds Default Implementation Inefficient
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/storage/UserStorage.java` (Lines 54-62) **Issue:** Default `findByIds()` loops calling `get()` individually **Severity:** MEDIUM (Performance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 74. Profile Activation Policy Duplicates Validation Logic

- canonical title: Profile Activation Policy Duplicates Validation Logic
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.3 Profile Activation Policy Duplicates Validation Logic
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `datingapp/core/workflow/ProfileActivationPolicy.java` (Lines 63-88) **Issue:** `missingFields()` mirrors `User.isComplete()` logic with comment acknowledging duplication **Severity:** MEDIUM (Maintainability) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 75. LocationService Hardcoded to Israel

- canonical title: LocationService Hardcoded to Israel
- severity: medium
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.4 LocationService Hardcoded to Israel
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/profile/LocationService.java` (Lines 36-56)   **Severity:** MEDIUM (Feature Limitation) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
private List<City> citiesFor(String countryCode) {
    return COUNTRY_IL.equalsIgnoreCase(countryCode) ? ISRAEL_CITIES : List.of();
}

## 76. Cleanup Methods Return Zero Without Implementation

- canonical title: Cleanup Methods Return Zero Without Implementation
- severity: medium
- category: general
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.5 Cleanup Methods Return Zero Without Implementation
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **Files:** - `UserStorage.purgeDeletedBefore()` returns 0 - `InteractionStorage.purgeDeletedBefore()` returns 0 **Impact:** GDPR compliance cleanup may not run **Severity:** MEDIUM (Compliance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 77. Photo URL Validation Missing

- canonical title: Photo URL Validation Missing
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.6 Photo URL Validation Missing
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `datingapp/core/model/User.java` (Line 456)   **Severity:** MEDIUM (Data Quality) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
public void addPhotoUrl(String url) {
    photoUrls.add(url);  // No validation
    touch();
}

## 78. Lifestyle Matcher Kids Compatibility Incomplete

- canonical title: Lifestyle Matcher Kids Compatibility Incomplete
- severity: medium
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.7 Lifestyle Matcher Kids Compatibility Incomplete
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/matching/LifestyleMatcher.java` (Lines 57-72) **Issue:** `areKidsStancesCompatible()` doesn't handle all combinations explicitly **Severity:** MEDIUM (Business Logic) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 79. Undo Service Window Expiry Uses System Clock

- canonical title: Undo Service Window Expiry Uses System Clock
- severity: medium
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.8 Undo Service Window Expiry Uses System Clock
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/matching/UndoService.java` (Lines 71-82) **Issue:** `canUndo()` and `getSecondsRemaining()` may not match storage clock **Severity:** MEDIUM (Correctness) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 80. Daily Limit Reset Ignores DST

- canonical title: Daily Limit Reset Ignores DST
- severity: medium
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.9 Daily Limit Reset Ignores DST
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/matching/DefaultDailyLimitService.java` (Lines 85-95) **Issue:** Uses `LocalDate.atStartOfDay()` which can be ambiguous during DST transitions **Severity:** MEDIUM (Correctness) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 81. Profile Completion Scoring Thresholds Are Magic Numbers

- canonical title: Profile Completion Scoring Thresholds Are Magic Numbers
- severity: medium
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.10 Profile Completion Scoring Thresholds Are Magic Numbers
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/profile/ProfileCompletionSupport.java` (Lines 35-48) **Issue:** Tier thresholds (95, 85, 70, 40) are hard-coded constants **Severity:** MEDIUM (Maintainability) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 82. Achievement Thresholds Duplicated

- canonical title: Achievement Thresholds Duplicated
- severity: medium
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.11 Achievement Thresholds Duplicated
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/metrics/EngagementDomain.java` (Lines 25-35) **Issue:** Thresholds in enum don't match config values in `DefaultAchievementService` **Severity:** MEDIUM (Consistency) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 83. Navigation Context Leak

- canonical title: Navigation Context Leak
- severity: medium
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.12 Navigation Context Leak
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/ui/NavigationService.java` (Lines 174-179)   **Impact:** User clicks profile from Standouts, but profile shows "Unknown user" **Severity:** MEDIUM (UX) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
NavigationContextEnvelope unconsumed = navigationContext.get();
if (unconsumed != null && logger.isDebugEnabled()) {
    logger.debug("Navigation context for {} was not consumed...", unconsumed.targetView);
}
// Context silently discarded

## 84. ImageCache Synchronous Loading on Virtual Threads

- canonical title: ImageCache Synchronous Loading on Virtual Threads
- severity: medium
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.13 ImageCache Synchronous Loading on Virtual Threads
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/ui/ImageCache.java` (Lines 158-165) **Issue:** `preload()` uses virtual threads but `getImage()` is synchronous **Impact:** Virtual thread pool exhaustion under heavy preload **Severity:** MEDIUM (Performance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 85. ProfileController Unsaved Changes Confirmation Gap

- canonical title: ProfileController Unsaved Changes Confirmation Gap
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.14 ProfileController Unsaved Changes Confirmation Gap
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `datingapp/ui/screen/ProfileController.java` (Lines 574-586) **Issue:** Confirmation check in `handleCancel()` and `handleBack()`, but not in `handleSave()` when validation fails **Impact:** User thinks they saved but validation failed, then navigates away losing changes **Severity:** MEDIUM (UX) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 86. SocialController Notification Read State Visual Lag

- canonical title: SocialController Notification Read State Visual Lag
- severity: medium
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.15 SocialController Notification Read State Visual Lag
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/ui/screen/SocialController.java` (Lines 105-130) **Issue:** Background style only updates on next `updateItem()` call (scroll out/in) **Severity:** MEDIUM (UX Polish) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 87. UiComponents TypingIndicator Animation Continues After Hide

- canonical title: UiComponents TypingIndicator Animation Continues After Hide
- severity: medium
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.16 UiComponents TypingIndicator Animation Continues After Hide
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/ui/UiComponents.java` (Lines 71-85)   **Impact:** Wasted CPU cycles; battery drain **Severity:** MEDIUM (Performance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
public void hide() {
    setVisible(false);
    setManaged(false);
    // Animations still running!
}

## 88. Missing Index on `messages(conversation_id)`

- canonical title: Missing Index on `messages(conversation_id)`
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.17 Missing Index on `messages(conversation_id)`
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/storage/schema/SchemaInitializer.java` (Lines 284-287) **Issue:** Only composite index exists; no standalone index **Severity:** MEDIUM (Performance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 89. Missing Index on `notifications(user_id, created_at)`

- canonical title: Missing Index on `notifications(user_id, created_at)`
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.19 Missing Index on `notifications(user_id, created_at)`
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/storage/schema/SchemaInitializer.java` (Lines 307-308) **Issue:** Query orders by `created_at DESC` but index only covers `(user_id, is_read)` **Impact:** Filesort operation **Severity:** MEDIUM (Performance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 90. Missing Index on `undo_states(user_id)`

- canonical title: Missing Index on `undo_states(user_id)`
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.20 Missing Index on `undo_states(user_id)`
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/storage/schema/SchemaInitializer.java` (Lines 419-421) **Issue:** Primary lookup is by `user_id` but only `expires_at` is indexed **Severity:** MEDIUM (Performance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 91. Friend Request Index Column Order Suboptimal

- canonical title: Friend Request Index Column Order Suboptimal
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.21 Friend Request Index Column Order Suboptimal
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/storage/schema/SchemaInitializer.java` (Lines 299-301) **Issue:** Index on `(to_user_id, status)` but query filters by status first **Severity:** MEDIUM (Performance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 92. No Migration for `daily_picks` Table in SchemaInitializer

- canonical title: No Migration for `daily_picks` Table in SchemaInitializer
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.22 No Migration for `daily_picks` Table in SchemaInitializer
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/storage/schema/SchemaInitializer.java` **Issue:** V2 migration creates `daily_picks` but `createAllTables()` doesn't include it **Impact:** Schema drift between fresh and upgraded databases **Severity:** MEDIUM (Data Integrity) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 93. Connection Pool Missing Validation Query

- canonical title: Connection Pool Missing Validation Query
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.23 Connection Pool Missing Validation Query
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `datingapp/storage/DatabaseManager.java` (Lines 55-64) **Issue:** No `setConnectionTestQuery()` configured **Impact:** Potential "connection is closed" errors after database restarts **Severity:** MEDIUM (Reliability) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 94. Connection Pool Missing Maximum Lifetime

- canonical title: Connection Pool Missing Maximum Lifetime
- severity: medium
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.24 Connection Pool Missing Maximum Lifetime
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/storage/DatabaseManager.java` (Lines 55-64) **Issue:** No `setMaxLifetime()` configured **Impact:** Connections may live indefinitely **Severity:** MEDIUM (Reliability) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 95. Dealbreakers Mapper Doesn't Read All Fields

- canonical title: Dealbreakers Mapper Doesn't Read All Fields
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.25 Dealbreakers Mapper Doesn't Read All Fields
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/storage/jdbi/JdbiUserStorage.java` (Lines 463-471) **Issue:** Dealbreakers read from legacy columns but NOT merged with normalized table data **Severity:** MEDIUM (Data Integrity) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 96. Missing CHECK Constraints

- canonical title: Missing CHECK Constraints
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.26 Missing CHECK Constraints
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/storage/schema/SchemaInitializer.java` (Lines 66-91) **Missing:** - `CHECK (min_age <= max_age)` - `CHECK (max_distance_km > 0)` - `CHECK (state != 'ENDED' OR ended_at IS NOT NULL)` **Severity:** MEDIUM (Data Integrity) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 97. No Cascade Delete Propagation for `deleted_at`

- canonical title: No Cascade Delete Propagation for `deleted_at`
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.27 No Cascade Delete Propagation for `deleted_at`
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/storage/schema/SchemaInitializer.java` (Lines 277-283) **Issue:** CASCADE DELETE exists but no `deleted_at` propagation **Impact:** Orphaned messages after conversation soft-delete **Severity:** MEDIUM (Data Integrity) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 98. Inefficient `getConversationsFor` Query

- canonical title: Inefficient `getConversationsFor` Query
- severity: medium
- category: performance
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.28 Inefficient `getConversationsFor` Query
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/storage/jdbi/JdbiConnectionStorage.java` (Lines 231-240)   **Issue:** `OR` condition and `COALESCE` prevent efficient index usage **Severity:** MEDIUM (Performance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - sql
SELECT ... FROM conversations
WHERE user_a = :userId OR user_b = :userId
ORDER BY COALESCE(last_message_at, created_at) DESC

## 99. Interface Contract Mismatches

- canonical title: Interface Contract Mismatches
- severity: medium
- category: performance
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.29 Interface Contract Mismatches
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **Files:** - `CommunicationStorage.countPendingFriendRequestsForUser()` default loads ALL then counts - `InteractionStorage.getMatchedCounterpartIds()` default loads ALL matches **Severity:** MEDIUM (Performance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 100. CLI Input Validation Gaps

- canonical title: CLI Input Validation Gaps
- severity: medium
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.30 CLI Input Validation Gaps
- conflict/status note: Status conflict: at least one source marks INVALID while others mark VALID/PARTIAL/RESOLVED.
- combined explanations:
  - **Files:** - `ProfileHandler.promptBirthDate()` silently skips on invalid input - `MessagingHandler.sendConversationMessage()` no length/profanity validation **Severity:** MEDIUM (UX/Security) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 101. Event Publishing Uses Magic Strings

- canonical title: Event Publishing Uses Magic Strings
- severity: medium
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.31 Event Publishing Uses Magic Strings
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/app/usecase/social/SocialUseCases.java` (Lines 126-138)   **Issue:** Should use `Match.MatchState` enum instead of strings **Severity:** MEDIUM (Maintainability) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
eventBus.publish(new AppEvent.RelationshipTransitioned(
        matchId, ..., "MATCHED", "UNMATCHED", ...));

## 102. Audit Logging Not Structured

- canonical title: Audit Logging Not Structured
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.32 Audit Logging Not Structured
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/app/usecase/social/SocialUseCases.java` (Lines 85-93)   **Issue:** No timestamp, IP, session info, correlation ID, or separate audit log **Severity:** MEDIUM (Compliance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
private void logModerationAction(String action, UUID actorId, UUID targetUserId, String reason) {
    logger.info("Moderation action={} actorId={} targetUserId={} reason={}", ...);
}

## 103. API Request Logging Missing

- canonical title: API Request Logging Missing
- severity: medium
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.33 API Request Logging Missing
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/app/api/RestApiServer.java` **Issue:** No request/response logging for audit trail **Severity:** MEDIUM (Compliance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 104. Session State Checks Inconsistent

- canonical title: Session State Checks Inconsistent
- severity: medium
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.34 Session State Checks Inconsistent
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/app/cli/MatchingHandler.java` (Lines 583-591) **Issue:** `browseWhoLikedMe()` doesn't check if user is ACTIVE **Severity:** MEDIUM (Security) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 105. REST API Error Codes Generic

- canonical title: REST API Error Codes Generic
- severity: medium
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.35 REST API Error Codes Generic
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/app/api/RestApiServer.java` (Lines 1045-1068) **Issue:** Unhandled exceptions map to 500; `CONFLICT` should be 409 **Severity:** MEDIUM (API Design) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 106. Rate Limiting Key Too Permissive

- canonical title: Rate Limiting Key Too Permissive
- severity: medium
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.36 Rate Limiting Key Too Permissive
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/app/api/RestApiServer.java` (Lines 993-1000)   **Issue:** Key is `IP + HTTP_METHOD` - same user with different methods bypasses limit **Severity:** MEDIUM (Security) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
String key = ctx.ip() + '|' + ctx.method();

## 107. LoginViewModel Auto-Complete Defaults Surprise Users

- canonical title: LoginViewModel Auto-Complete Defaults Surprise Users
- severity: medium
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.37 LoginViewModel Auto-Complete Defaults Surprise Users
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/ui/viewmodel/LoginViewModel.java` (Lines 125-155) **Issue:** Incomplete profiles auto-filled with defaults (Tel Aviv location, placeholder bio) without consent **Severity:** MEDIUM (UX) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 108. MatchesController Particle Layer Cleanup Incomplete

- canonical title: MatchesController Particle Layer Cleanup Incomplete
- severity: medium
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.38 MatchesController Particle Layer Cleanup Incomplete
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/ui/screen/MatchesController.java` (Lines 108-145) **Issue:** Individual heart particle animations not tracked for cleanup **Impact:** Memory leak if navigating away mid-animation **Severity:** MEDIUM (Performance) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 109. ViewModelFactory Session Listener Removal Order

- canonical title: ViewModelFactory Session Listener Removal Order
- severity: medium
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.39 ViewModelFactory Session Listener Removal Order
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/ui/viewmodel/ViewModelFactory.java` (Lines 208-218) **Issue:** ViewModels disposed before session listener unbound in `reset()` **Impact:** Race condition during logout **Severity:** MEDIUM (Correctness) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 110. StandoutsController Selection Triggers Navigation Immediately

- canonical title: StandoutsController Selection Triggers Navigation Immediately
- severity: medium
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.40 StandoutsController Selection Triggers Navigation Immediately
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/ui/screen/StandoutsController.java` (Lines 58-64) **Issue:** Selecting a standout immediately navigates; user cannot browse multiple before selecting **Severity:** MEDIUM (UX) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 111. PreferencesController Slider Labels May Show Stale Values

- canonical title: PreferencesController Slider Labels May Show Stale Values
- severity: medium
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.41 PreferencesController Slider Labels May Show Stale Values
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/ui/screen/PreferencesController.java` (Lines 73-95) **Issue:** Labels don't sync if ViewModel updated programmatically **Severity:** MEDIUM (UX Polish) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 112. ChatController Message Length Indicator Style Race

- canonical title: ChatController Message Length Indicator Style Race
- severity: medium
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.42 ChatController Message Length Indicator Style Race
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/ui/screen/ChatController.java` (Lines 162-167) **Issue:** Style class update may race with text clear **Severity:** MEDIUM (UX Polish) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 113. REST API Deliberate Exceptions

- canonical title: REST API Deliberate Exceptions
- severity: medium
- category: architecture
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.43 REST API Deliberate Exceptions
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/app/api/RestApiServer.java` (Lines 178, 186, 199, 207) **Issue:** Comments like "Deliberate exception: read-only local admin/discovery route" indicate architectural shortcuts **Severity:** MEDIUM (Architecture) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 114. Multiple Constructor Overloads for Backward Compatibility

- canonical title: Multiple Constructor Overloads for Backward Compatibility
- severity: medium
- category: general
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.44 Multiple Constructor Overloads for Backward Compatibility
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **Files:** - `MatchingUseCases.java` - 5 constructors - `ProfileUseCases.java` - 5 constructors - `MessagingUseCases.java` - 2 constructors **Severity:** MEDIUM (Maintainability) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 115. RecommendationService Wraps Delegated Services

- canonical title: RecommendationService Wraps Delegated Services
- severity: medium
- category: architecture
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.45 RecommendationService Wraps Delegated Services
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/matching/RecommendationService.java` (Lines 109-159) **Issue:** Wraps services with pass-through methods without clear benefit **Severity:** MEDIUM (Architecture) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 116. UiDialogs Report Description Not Captured

- canonical title: UiDialogs Report Description Not Captured
- severity: medium
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 3: MEDIUM SEVERITY ISSUES > 3.47 UiDialogs Report Description Not Captured
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/ui/UiDialogs.java` (Lines 36-59) **Issue:** `showReportDialog()` doesn't pass description to consumer (always null) **Severity:** MEDIUM (UX) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 117. Inconsistent requireLogin Usage in CLI

- canonical title: Inconsistent requireLogin Usage in CLI
- severity: medium
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.1 Inconsistent requireLogin Usage in CLI
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **Files:** `MatchingHandler.java`, `MessagingHandler.java` Several methods manually check for `currentUser == null` instead of using centralized `CliTextAndInput.requireLogin()` wrapper: - `MatchingHandler.java` lines 729-731, 801-804 - `MessagingHandler.java` lines 62-66, 403-407 ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - - `MatchingHandler.java` lines 729-731, 801-804
  - - `MessagingHandler.java` lines 62-66, 403-407

## 118. Silent Error Handling in CLI

- canonical title: Silent Error Handling in CLI
- severity: medium
- category: general
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.2 Silent Error Handling in CLI
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - Multiple catch blocks silently ignore exceptions without user feedback: - `ProfileHandler.java` lines 307, 452, 516, 557, 599, 654 - `MessagingHandler.java` line 143 ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - - `ProfileHandler.java` lines 307, 452, 516, 557, 599, 654
  - - `MessagingHandler.java` line 143

## 119. Input Validation Missing in CLI

- canonical title: Input Validation Missing in CLI
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.3 Input Validation Missing in CLI
- conflict/status note: Status signals: VALID.
- combined explanations:
  - Several input prompts lack proper validation: - `ProfileHandler.java` line 284: No validation for maximum bio length - `ProfileHandler.java` lines 627-631: No URL validation - `ProfileHandler.java` line 1034: No validation for name length ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - - `ProfileHandler.java` line 284: No validation for maximum bio length
  - - `ProfileHandler.java` lines 627-631: No URL validation
  - - `ProfileHandler.java` line 1034: No validation for name length

## 120. Inconsistent Case Conversion in CLI Input

- canonical title: Inconsistent Case Conversion in CLI Input
- severity: medium
- category: general
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.4 Inconsistent Case Conversion in CLI Input
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - Different handlers use different approaches for case-insensitive input: - `MatchingHandler.java` line 306: `.toLowerCase(Locale.ROOT)` - `MatchingHandler.java` line 657: `.toUpperCase(Locale.ROOT)` - Different!
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - - `MatchingHandler.java` line 306: `.toLowerCase(Locale.ROOT)`
  - - `MatchingHandler.java` line 657: `.toUpperCase(Locale.ROOT)` - Different!

## 121. Inconsistent User Data Saving in CLI

- canonical title: Inconsistent User Data Saving in CLI
- severity: medium
- category: general
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.5 Inconsistent User Data Saving in CLI
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - Several methods modify user properties without explicit saves, relying on later calls to `saveProfile()`.
  - This could cause data loss if flow is interrupted.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 122. RestApiServer Missing Input Validation

- canonical title: RestApiServer Missing Input Validation
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.6 RestApiServer Missing Input Validation
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `app/api/RestApiServer.java` (lines 252-281) The `ProfileUpdateRequest` validation doesn't validate: - `latitude` / `longitude` ranges - `maxDistanceKm`, `minAge`, `maxAge` boundaries - `heightCm` boundaries - `interests` set size limit ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `app/api/RestApiServer.java` (lines 252-281)

## 123. RestApiServer Missing Authorization Check

- canonical title: RestApiServer Missing Authorization Check
- severity: medium
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.7 RestApiServer Missing Authorization Check
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/api/RestApiServer.java` (lines 283-293) `getCandidates()` only checks if user exists but doesn't verify acting user authorization.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `app/api/RestApiServer.java` (lines 283-293)

## 124. CleanupScheduler Thread Safety

- canonical title: CleanupScheduler Thread Safety
- severity: medium
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.8 CleanupScheduler Thread Safety
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/bootstrap/CleanupScheduler.java` (lines 35-49) The `isRunning()` method reads without synchronization, which could cause visibility issues.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `app/bootstrap/CleanupScheduler.java` (lines 35-49)

## 125. Configuration Env Var Coverage Incomplete

- canonical title: Configuration Env Var Coverage Incomplete
- severity: medium
- category: testing
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.9 Configuration Env Var Coverage Incomplete
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/bootstrap/ApplicationStartup.java` (lines 219-240` Many config fields don't have env var overrides: - Missing: `nearbyDistanceKm`, `closeDistanceKm`, `similarAgeDiff` - Missing: `autoBanThreshold` - Missing: `suspiciousSwipeVelocity`, all weight parameters ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `app/bootstrap/ApplicationStartup.java` (lines 219-240`

## 126. ValidationService Email Validation Limited

- canonical title: ValidationService Email Validation Limited
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.10 ValidationService Email Validation Limited
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `core/profile/ValidationService.java` (line 32) EMAIL_PATTERN regex doesn't handle all valid email RFC 5322 characters.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `core/profile/ValidationService.java` (line 32)

## 127. ValidationService Phone Normalization Doesn't Normalize

- canonical title: ValidationService Phone Normalization Doesn't Normalize
- severity: medium
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.11 ValidationService Phone Normalization Doesn't Normalize
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `core/profile/ValidationService.java` (lines 291-304) `normalizePhone()` validates but doesn't actually normalize the phone number.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `core/profile/ValidationService.java` (lines 291-304)

## 128. CandidateFinder Cache Fingerprint Incomplete

- canonical title: CandidateFinder Cache Fingerprint Incomplete
- severity: medium
- category: performance
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.12 CandidateFinder Cache Fingerprint Incomplete
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `core/matching/CandidateFinder.java` (lines 227-238) `candidateFingerprint()` doesn't include `dealbreakers`, `interests`, and lifestyle fields in cache key.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `core/matching/CandidateFinder.java` (lines 227-238)

## 129. RelationshipWorkflowPolicy Block Not in Allowed Transitions [INVALID] ❌

- canonical title: RelationshipWorkflowPolicy Block Not in Allowed Transitions [INVALID] ❌
- severity: medium
- category: general
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.14 RelationshipWorkflowPolicy Block Not in Allowed Transitions [INVALID] ❌
- conflict/status note: Status conflict: at least one source marks INVALID while others mark VALID/PARTIAL/RESOLVED.
- combined explanations:
  - **File:** `core/workflow/RelationshipWorkflowPolicy.java` (lines 18-21) The `ALLOWED_TRANSITIONS` map shows BLOCK can only come from ACTIVE state, but `Match.block()` doesn't enforce this.
  - **Verification Note:** [INVALID] ❌ Triple-verification confirms this is intentional.
  - Blocking is governed by trust/safety logic, not just matching workflow.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `core/workflow/RelationshipWorkflowPolicy.java` (lines 18-21)

## 130. Memory Leak in Nested Subscriptions

- canonical title: Memory Leak in Nested Subscriptions
- severity: medium
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.15 Memory Leak in Nested Subscriptions
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `ui/screen/DashboardController.java` (line 182) Nested subscription created inside subscription - inner subscription may leak if outer is cleaned up improperly.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `ui/screen/DashboardController.java` (line 182)

## 131. Memory Leak in Card Cache

- canonical title: Memory Leak in Card Cache
- severity: medium
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.16 Memory Leak in Card Cache
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `ui/screen/MatchesController.java` (lines 137-145) Card caches are only cleared in `cleanup()`.
  - If controller is reused without proper cleanup, cached VBoxes could accumulate.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `ui/screen/MatchesController.java` (lines 137-145)

## 132. Race Condition in Card Swiping

- canonical title: Race Condition in Card Swiping
- severity: medium
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.17 Race Condition in Card Swiping
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `ui/screen/MatchingController.java` (lines 352-378) `cardTransitionInProgress` is a boolean primitive, not thread-safe.
  - Multiple rapid swipes could bypass the check.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `ui/screen/MatchingController.java` (lines 352-378)

## 133. Listener Not Properly Removed

- canonical title: Listener Not Properly Removed
- severity: medium
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 3: MEDIUM Priority Issues > 3.18 Listener Not Properly Removed
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `ui/screen/ChatController.java` (lines 159, 173-178) Listener added to ObservableList but only removed in cleanup().
  - Could cause ConcurrentModificationException during rapid message updates.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `ui/screen/ChatController.java` (lines 159, 173-178)

## 134. Archived Utils in docs/ Folder

- canonical title: Archived Utils in docs/ Folder
- severity: low
- category: general
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.1 Archived Utils in docs/ Folder
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **Files:** `docs/archived-utils/` contains 3 Java files (`AnimationHelper.java`, `AsyncExecutor.java`, `ButtonFeedback.java`) **Impact:** Dead code in repository **Severity:** LOW ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 135. Deliberate Exceptions in REST API Layer

- canonical title: Deliberate Exceptions in REST API Layer
- severity: low
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.2 Deliberate Exceptions in REST API Layer
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/app/api/RestApiServer.java` multiple locations **Issue:** Use-case layer bypassed for some routes **Severity:** LOW (Architecture) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 136. ConnectionModels Record Constructors Verbose

- canonical title: ConnectionModels Record Constructors Verbose
- severity: low
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.3 ConnectionModels Record Constructors Verbose
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `datingapp/core/connection/ConnectionModels.java` **Issue:** Many records have explicit validation that could be simplified **Severity:** LOW (Code Quality) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 137. InterestMatcher Shared Interests Preview Count Hard-Coded

- canonical title: InterestMatcher Shared Interests Preview Count Hard-Coded
- severity: low
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.4 InterestMatcher Shared Interests Preview Count Hard-Coded
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/matching/InterestMatcher.java` (Line 23)   **Severity:** LOW (Maintainability) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
private static final int SHARED_INTERESTS_PREVIEW_COUNT = 3;

## 138. MatchQualityService Highlight Generation Order Fixed

- canonical title: MatchQualityService Highlight Generation Order Fixed
- severity: low
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.5 MatchQualityService Highlight Generation Order Fixed
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/matching/MatchQualityService.java` (Lines 295-312) **Issue:** Highlights always in same order; no prioritization **Severity:** LOW (UX Polish) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 139. DefaultCompatibilityCalculator Activity Score Thresholds Duplicated

- canonical title: DefaultCompatibilityCalculator Activity Score Thresholds Duplicated
- severity: low
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.6 DefaultCompatibilityCalculator Activity Score Thresholds Duplicated
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/matching/DefaultCompatibilityCalculator.java` (Lines 52-58) **Issue:** Same thresholds exist in `RecommendationService` **Severity:** LOW (Maintainability) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 140. BaseViewModel Missing Null Check in Error Router

- canonical title: BaseViewModel Missing Null Check in Error Router
- severity: low
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.7 BaseViewModel Missing Null Check in Error Router
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/ui/viewmodel/BaseViewModel.java` (Lines 36-40) **Issue:** `errorSink` can be null; null supplier pattern inconsistent **Severity:** LOW (Code Quality) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 141. Disabled Architecture Tests

- canonical title: Disabled Architecture Tests
- severity: low
- category: testing
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.8 Disabled Architecture Tests
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/architecture/TimePolicyArchitectureTest.java` **Tests:** - `noFeatureCodeUsesZoneIdSystemDefault()` - "Enable after WU-14 completes timezone rollout" - `noFeatureCodeUsesAppConfigDefaults()` - "Enable after WU-14 completes config rollout" **Assessment:** Intentionally disabled; acceptable practice **Severity:** LOW (Technical Debt) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 142. REST API Generic Exception Handlers

- canonical title: REST API Generic Exception Handlers
- severity: low
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.9 REST API Generic Exception Handlers
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/app/api/RestApiServer.java` (Lines 1045-1068) **Issue:** Loses business context in error mapping **Severity:** LOW (API Design) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 143. Incomplete Use-Case Coverage

- canonical title: Incomplete Use-Case Coverage
- severity: low
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.10 Incomplete Use-Case Coverage
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **Missing:** - `MatchingUseCases.getMatchById()` - `MatchingUseCases.getMatchesByState()` - `MatchingUseCases.searchCandidates()` - `MessagingUseCases.searchMessages()` - `MessagingUseCases.exportConversation()` - `MessagingUseCases.markMultipleRead()` - `ProfileUseCases.getProfileById()` - `ProfileUseCases.updatePhotoUrls()` - `ProfileUseCases.verifyProfile()` **Severity:** LOW (Feature Completeness) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 144. MatchingViewModel Photo Navigation Doesn't Update Note State

- canonical title: MatchingViewModel Photo Navigation Doesn't Update Note State
- severity: low
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.11 MatchingViewModel Photo Navigation Doesn't Update Note State
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/ui/viewmodel/MatchingViewModel.java` (Lines 258-269) **Issue:** Future-proofing gap; no current impact **Severity:** LOW ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 145. User Entity `isInterestedInEveryone()` Logic Fragile

- canonical title: User Entity `isInterestedInEveryone()` Logic Fragile
- severity: low
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.12 User Entity `isInterestedInEveryone()` Logic Fragile
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/model/User.java` (Lines 288-301) **Issue:** Relies on `interestedIn` containing ALL genders; nothing prevents removing one later **Severity:** LOW (Edge Case) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 146. MatchPreferences Dealbreaker Builder Allows Invalid Ranges

- canonical title: MatchPreferences Dealbreaker Builder Allows Invalid Ranges
- severity: low
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.13 MatchPreferences Dealbreaker Builder Allows Invalid Ranges
- conflict/status note: Status conflict: at least one source marks INVALID while others mark VALID/PARTIAL/RESOLVED.
- combined explanations:
  - **File:** `datingapp/core/profile/MatchPreferences.java` (Lines 330-355) **Issue:** Builder's `heightRange()` validation could be clearer **Severity:** LOW (API Design) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 147. SwipeState.Session Match Count Validation Incomplete

- canonical title: SwipeState.Session Match Count Validation Incomplete
- severity: low
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.14 SwipeState.Session Match Count Validation Incomplete
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `datingapp/core/metrics/SwipeState.java` (Lines 89-95) **Issue:** Constructor validates but `incrementMatchCount()` check is weak **Severity:** LOW (Data Integrity) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 148. DefaultStandoutService Standout Generation Doesn't Respect Limits

- canonical title: DefaultStandoutService Standout Generation Doesn't Respect Limits
- severity: low
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.15 DefaultStandoutService Standout Generation Doesn't Respect Limits
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `datingapp/core/matching/DefaultStandoutService.java` (Lines 94-108) **Issue:** Config value may not be validated **Severity:** LOW (Configuration) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 149. MatchQualityService Division by Zero Risk

- canonical title: MatchQualityService Division by Zero Risk
- severity: low
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.16 MatchQualityService Division by Zero Risk
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/matching/MatchQualityService.java` (Lines 224-229) **Assessment:** Actually handled with `<= 0` check **Severity:** LOW (Edge Case - Already Mitigated) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 150. ConnectionService Message Sanitization Strips ALL HTML

- canonical title: ConnectionService Message Sanitization Strips ALL HTML
- severity: low
- category: security
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.17 ConnectionService Message Sanitization Strips ALL HTML
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/connection/ConnectionService.java` (Lines 79-85) **Assessment:** Security feature; may be intentional **Severity:** LOW (Feature Decision) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 151. RelationshipWorkflowPolicy State Machine Doesn't Cover All Transitions

- canonical title: RelationshipWorkflowPolicy State Machine Doesn't Cover All Transitions
- severity: low
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md: 1
- source_files:
  - CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md
- source_sections:
  - 🔍 COMPREHENSIVE CODEBASE AUDIT REPORT > PART 4: LOW SEVERITY ISSUES > 4.18 RelationshipWorkflowPolicy State Machine Doesn't Cover All Transitions
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `datingapp/core/workflow/RelationshipWorkflowPolicy.java` (Lines 23-26) **Assessment:** Intentional (terminal states); `isTerminal()` should reflect this **Severity:** LOW (Documentation) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 152. No Caching Layer in Storage

- canonical title: No Caching Layer in Storage
- severity: low
- category: performance
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 4: LOW Priority Issues > 4.1 No Caching Layer in Storage
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - No caching mechanism anywhere in storage layer.
  - Every query hits database directly.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 153. Inconsistent UNIQUE Constraint Naming

- canonical title: Inconsistent UNIQUE Constraint Naming
- severity: low
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 4: LOW Priority Issues > 4.3 Inconsistent UNIQUE Constraint Naming
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `storage/schema/SchemaInitializer.java` Inconsistent naming convention (`uk_` vs `unq_`).
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 154. Unnecessary AUTO_INCREMENT ID in profile_views

- canonical title: Unnecessary AUTO_INCREMENT ID in profile_views
- severity: low
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 4: LOW Priority Issues > 4.4 Unnecessary AUTO_INCREMENT ID in profile_views
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `storage/schema/SchemaInitializer.java` (line 391) Column is defined but not used by queries.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `storage/schema/SchemaInitializer.java` (line 391)

## 155. Messaging Command Parsing Limited

- canonical title: Messaging Command Parsing Limited
- severity: low
- category: general
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 4: LOW Priority Issues > 4.5 Messaging Command Parsing Limited
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/cli/MessagingHandler.java` (lines 336-350) Only four commands recognized: `/back`, `/older`, `/block`, `/unmatch`.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `app/cli/MessagingHandler.java` (lines 336-350)

## 156. Gracesful Exit Feature Underutilized

- canonical title: Gracesful Exit Feature Underutilized
- severity: low
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 4: LOW Priority Issues > 4.6 Gracesful Exit Feature Underutilized
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/cli/MatchingHandler.java` (lines 435-443) Graceful exit option exists but may need more prominence.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `app/cli/MatchingHandler.java` (lines 435-443)

## 157. HandlerPolicy.REQUIRED Never Used

- canonical title: HandlerPolicy.REQUIRED Never Used
- severity: low
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 4: LOW Priority Issues > 4.7 HandlerPolicy.REQUIRED Never Used
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `app/event/InProcessAppEventBus.java` (lines 36-37) All actual event subscriptions use `BEST_EFFORT`.
  - No events require strict handling.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `app/event/InProcessAppEventBus.java` (lines 36-37)

## 158. Config Validation Doesn't Check Monotonicity

- canonical title: Config Validation Doesn't Check Monotonicity
- severity: low
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 4: LOW Priority Issues > 4.8 Config Validation Doesn't Check Monotonicity
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `core/AppConfigValidator.java` For response time thresholds, values should be monotonically increasing but aren't validated.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 159. StandoutsController Navigation Wrong

- canonical title: StandoutsController Navigation Wrong
- severity: low
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 4: LOW Priority Issues > 4.9 StandoutsController Navigation Wrong
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `ui/screen/StandoutsController.java` (lines 77-78) Navigates to `PROFILE_VIEW` but should likely navigate to `MATCHING`.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `ui/screen/StandoutsController.java` (lines 77-78)

## 160. Unused import in MainMenuRegistry

- canonical title: Unused import in MainMenuRegistry
- severity: low
- category: general
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 4: LOW Priority Issues > 4.10 Unused import in MainMenuRegistry
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - Potential unnecessary complexity in static imports.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 161. Status: RESOLVED 🟢

- canonical title: Status: RESOLVED 🟢
- severity: unspecified
- category: general
- mention_count_total: 9
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 9
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.2 Code Architecture & Organization > FI-CONS-001: God Object - DatabaseManager
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.2 Code Architecture & Organization > FI-CONS-003: Core Entity Bloat - User.java
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.3 Redundancy & Duplication > FI-CONS-004: Duplicated Validation Logic
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.3 Redundancy & Duplication > FI-CONS-005: Proliferation of Test Stubs
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.3 Redundancy & Duplication > FI-CONS-006: CSS Redundancy & Hardcoded Colors
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.4 Technical Debt & Smells > FI-CONS-007: Broad Exception Catching - MatchingService
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.4 Technical Debt & Smells > FI-CONS-008: Manual Dependency Injection Boilerplate
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.4 Technical Debt & Smells > FI-CONS-009: Scattered TODOs
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.6 Storage & JDBI Optimization > FI-CONS-014: Documentation Drift
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - *   **Status**: RESOLVED 🟢
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 162. Event-Driven: InProcessAppEventBus for component communication

- canonical title: Event-Driven: InProcessAppEventBus for component communication
- severity: unspecified
- category: ui-ux
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 1. Architecture Overview > Key Architectural Patterns
- conflict/status note: Status signals: VALID.
- combined explanations:
  - - **Event-Driven**: InProcessAppEventBus for component communication - **Async Abstractions**: Custom async framework for UI operations - **Result Records**: Business logic returns typed results instead of exceptions - **Builder Pattern**: Complex object construction (User.StorageBuilder, Dealbreakers.Builder)
  - - **Event-Driven**: InProcessAppEventBus for component communication - **Async Abstractions**: Custom async framework for UI operations - **Result Records**: Business logic returns typed results instead of exceptions - **Builder Pattern**: Complex object construction with validation
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 163. Validation: Comprehensive config validation with descriptive errors

- canonical title: Validation: Comprehensive config validation with descriptive errors
- severity: unspecified
- category: data-integrity
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 2. Entry Points and Bootstrap > Configuration System
- conflict/status note: Status signals: VALID.
- combined explanations:
  - - **Validation**: Comprehensive config validation with descriptive errors - **Timezone Handling**: Custom ZoneId deserializer for user timezone config
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 164. Identity: UUID-based with deterministic timestamps

- canonical title: Identity: UUID-based with deterministic timestamps
- severity: unspecified
- category: data-integrity
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 3. Data Models and Relationships > Core Domain Models > User Model (`User.java`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Identity**: UUID-based with deterministic timestamps - **Profile Data**: Name, bio, birth date, gender, location, photos
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 165. Preferences: Gender interests, age range, distance limits

- canonical title: Preferences: Gender interests, age range, distance limits
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 3. Data Models and Relationships > Core Domain Models > User Model (`User.java`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Preferences**: Gender interests, age range, distance limits
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 166. Lifestyle: Smoking, drinking, kids stance, education, height

- canonical title: Lifestyle: Smoking, drinking, kids stance, education, height
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 3. Data Models and Relationships > Core Domain Models > User Model (`User.java`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Lifestyle**: Smoking, drinking, kids stance, education, height
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 167. Verification: Email/phone verification workflow

- canonical title: Verification: Email/phone verification workflow
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 3. Data Models and Relationships > Core Domain Models > User Model (`User.java`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Verification**: Email/phone verification workflow - **Pace Preferences**: Communication style and dating timeline preferences
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 168. Dealbreakers: Hard filters for matching compatibility

- canonical title: Dealbreakers: Hard filters for matching compatibility
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 3. Data Models and Relationships > Core Domain Models > User Model (`User.java`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Dealbreakers**: Hard filters for matching compatibility
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 169. Interests: Categorized activity preferences (max 10 per user)

- canonical title: Interests: Categorized activity preferences (max 10 per user)
- severity: unspecified
- category: data-integrity
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 3. Data Models and Relationships > Core Domain Models > User Model (`User.java`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Interests**: Categorized activity preferences (max 10 per user) - **State Management**: INCOMPLETE → ACTIVE ↔ PAUSED → BANNED transitions - **Soft Delete**: Deleted timestamp for data retention **Relationships:** - User ↔ Match (many-to-many via Match entity) - User ↔ ProfileNote (admin notes on user profiles) - User → MatchPreferences (nested preferences and dealbreakers) - User → Interests (enum set with categories)
  - - **Interests**: Categorized activity preferences (max 10 per user) - **State Management**: INCOMPLETE → ACTIVE ↔ PAUSED → BANNED transitions - **Soft Delete**: Deleted timestamp for data retention **Relationships:** - User ↔ Match (many-to-many via Match entity) - User ↔ ProfileNote (admin notes on user profiles) - User → MatchPreferences (nested preferences and dealbreakers)
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 170. Identity: Deterministic string ID from sorted UUID pair

- canonical title: Identity: Deterministic string ID from sorted UUID pair
- severity: unspecified
- category: ui-ux
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 3. Data Models and Relationships > Core Domain Models > Match Model (`Match.java`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Identity**: Deterministic string ID from sorted UUID pair
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 171. Participants: userA and userB (lexicographically ordered)

- canonical title: Participants: userA and userB (lexicographically ordered)
- severity: unspecified
- category: concurrency
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 3. Data Models and Relationships > Core Domain Models > Match Model (`Match.java`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Participants**: userA and userB (lexicographically ordered) - **State Machine**: ACTIVE → FRIENDS/UNMATCHED/GRACEFUL_EXIT/BLOCKED
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 172. Lifecycle: Created timestamp, ended timestamp, ended by user

- canonical title: Lifecycle: Created timestamp, ended timestamp, ended by user
- severity: unspecified
- category: data-integrity
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 3. Data Models and Relationships > Core Domain Models > Match Model (`Match.java`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Lifecycle**: Created timestamp, ended timestamp, ended by user - **Archive Reasons**: Categorization for analytics (FRIEND_ZONE, UNMATCH, BLOCK) - **Soft Delete**: Deleted timestamp support - **Messaging Permissions**: State-based messaging eligibility **Relationships:** - Match → User (two-way association) - Match lifecycle managed by TrustSafetyService and MatchingService
  - - **Lifecycle**: Created timestamp, ended timestamp, ended by user - **Archive Reasons**: Categorization for analytics (FRIEND_ZONE, UNMATCH, BLOCK) - **Soft Delete**: Deleted timestamp support - **Messaging Permissions**: State-based messaging eligibility
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 173. Identity: Author-subject UUID pair

- canonical title: Identity: Author-subject UUID pair
- severity: unspecified
- category: security
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 3. Data Models and Relationships > Core Domain Models > ProfileNote Model (`ProfileNote.java`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Identity**: Author-subject UUID pair
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 174. Content: 500-character limit admin notes

- canonical title: Content: 500-character limit admin notes
- severity: unspecified
- category: data-integrity
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 3. Data Models and Relationships > Core Domain Models > ProfileNote Model (`ProfileNote.java`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Content**: 500-character limit admin notes - **Audit Trail**: Created/updated timestamps
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 175. Constraints: Cannot note yourself, content required

- canonical title: Constraints: Cannot note yourself, content required
- severity: unspecified
- category: ui-ux
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 3. Data Models and Relationships > Core Domain Models > ProfileNote Model (`ProfileNote.java`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Constraints**: Cannot note yourself, content required
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 176. UserStorage: CRUD operations for user profiles and preferences

- canonical title: UserStorage: CRUD operations for user profiles and preferences
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 5. Storage Layer Implementation > Storage Interfaces
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **UserStorage**: CRUD operations for user profiles and preferences
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 177. InteractionStorage: Match and swipe data management

- canonical title: InteractionStorage: Match and swipe data management
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 5. Storage Layer Implementation > Storage Interfaces
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **InteractionStorage**: Match and swipe data management
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 178. CommunicationStorage: Messaging and conversation persistence

- canonical title: CommunicationStorage: Messaging and conversation persistence
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 5. Storage Layer Implementation > Storage Interfaces
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **CommunicationStorage**: Messaging and conversation persistence
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 179. AnalyticsStorage: Metrics and activity data

- canonical title: AnalyticsStorage: Metrics and activity data
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 5. Storage Layer Implementation > Storage Interfaces
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **AnalyticsStorage**: Metrics and activity data
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 180. TrustSafetyStorage: Safety reports and blocks

- canonical title: TrustSafetyStorage: Safety reports and blocks
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 5. Storage Layer Implementation > Storage Interfaces
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **TrustSafetyStorage**: Safety reports and blocks
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 181. NavigationService: Singleton navigation coordinator

- canonical title: NavigationService: Singleton navigation coordinator
- severity: unspecified
- category: architecture
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 6. UI Architecture > JavaFX Application Structure
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **NavigationService**: Singleton navigation coordinator
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 182. ViewModelFactory: Service-to-UI adapter factory

- canonical title: ViewModelFactory: Service-to-UI adapter factory
- severity: unspecified
- category: ui-ux
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 6. UI Architecture > JavaFX Application Structure
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **ViewModelFactory**: Service-to-UI adapter factory - **Screen Controllers**: FXML-backed UI controllers
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 183. ViewModels: Business logic containers with async abstractions

- canonical title: ViewModels: Business logic containers with async abstractions
- severity: unspecified
- category: ui-ux
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 6. UI Architecture > JavaFX Application Structure
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **ViewModels**: Business logic containers with async abstractions
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 184. ViewModelAsyncScope: Structured async operation management

- canonical title: ViewModelAsyncScope: Structured async operation management
- severity: unspecified
- category: ui-ux
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 6. UI Architecture > Async Framework (`ui/async/`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **ViewModelAsyncScope**: Structured async operation management
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 185. AsyncErrorRouter: Centralized error handling for UI operations

- canonical title: AsyncErrorRouter: Centralized error handling for UI operations
- severity: unspecified
- category: ui-ux
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 6. UI Architecture > Async Framework (`ui/async/`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **AsyncErrorRouter**: Centralized error handling for UI operations
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 186. UiThreadDispatcher: Platform-specific threading abstractions

- canonical title: UiThreadDispatcher: Platform-specific threading abstractions
- severity: unspecified
- category: concurrency
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 6. UI Architecture > Async Framework (`ui/async/`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **UiThreadDispatcher**: Platform-specific threading abstractions
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 187. TaskHandle: Cancellable operation management

- canonical title: TaskHandle: Cancellable operation management
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 6. UI Architecture > Async Framework (`ui/async/`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **TaskHandle**: Cancellable operation management
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 188. Controllers: 12 screen controllers for different app sections

- canonical title: Controllers: 12 screen controllers for different app sections
- severity: unspecified
- category: ui-ux
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 6. UI Architecture > UI Components
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Controllers**: 12 screen controllers for different app sections
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 189. Popups: Match notifications and milestone celebrations

- canonical title: Popups: Match notifications and milestone celebrations
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 6. UI Architecture > UI Components
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Popups**: Match notifications and milestone celebrations
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 190. Animations: Smooth transitions and feedback

- canonical title: Animations: Smooth transitions and feedback
- severity: unspecified
- category: ui-ux
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 6. UI Architecture > UI Components
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Animations**: Smooth transitions and feedback - **Image Management**: Caching and local photo storage
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 191. ProfileHandler: User creation, selection, profile completion

- canonical title: ProfileHandler: User creation, selection, profile completion
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 7. CLI Implementation > Handler Architecture
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **ProfileHandler**: User creation, selection, profile completion
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 192. MatchingHandler: Candidate browsing, matches viewing, notifications

- canonical title: MatchingHandler: Candidate browsing, matches viewing, notifications
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 7. CLI Implementation > Handler Architecture
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **MatchingHandler**: Candidate browsing, matches viewing, notifications
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 193. MessagingHandler: Conversation management

- canonical title: MessagingHandler: Conversation management
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 7. CLI Implementation > Handler Architecture
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **MessagingHandler**: Conversation management
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 194. SafetyHandler: Blocking, reporting, safety features

- canonical title: SafetyHandler: Blocking, reporting, safety features
- severity: unspecified
- category: feature-gap
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 7. CLI Implementation > Handler Architecture
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **SafetyHandler**: Blocking, reporting, safety features
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 195. StatsHandler: Statistics and achievements display

- canonical title: StatsHandler: Statistics and achievements display
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 7. CLI Implementation > Handler Architecture
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **StatsHandler**: Statistics and achievements display
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 196. CliTextAndInput: UTF-8 aware console I/O with input validation

- canonical title: CliTextAndInput: UTF-8 aware console I/O with input validation
- severity: unspecified
- category: data-integrity
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 7. CLI Implementation > Input/Output Abstraction
- conflict/status note: Status signals: VALID.
- combined explanations:
  - - **CliTextAndInput**: UTF-8 aware console I/O with input validation
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 197. MainMenuRegistry: Dynamic menu construction with login requirements

- canonical title: MainMenuRegistry: Dynamic menu construction with login requirements
- severity: unspecified
- category: ui-ux
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 7. CLI Implementation > Input/Output Abstraction
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **MainMenuRegistry**: Dynamic menu construction with login requirements
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 198. MessagingUseCases: Communication business rules

- canonical title: MessagingUseCases: Communication business rules
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 8. Use Case Layer > Business Logic Organization (`app/usecase/`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **MessagingUseCases**: Communication business rules
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 199. ProfileUseCases: Profile management operations

- canonical title: ProfileUseCases: Profile management operations
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 8. Use Case Layer > Business Logic Organization (`app/usecase/`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **ProfileUseCases**: Profile management operations
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 200. SocialUseCases: Social features and interactions

- canonical title: SocialUseCases: Social features and interactions
- severity: unspecified
- category: feature-gap
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 8. Use Case Layer > Business Logic Organization (`app/usecase/`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **SocialUseCases**: Social features and interactions
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 201. UseCaseError: Typed error results instead of exceptions

- canonical title: UseCaseError: Typed error results instead of exceptions
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 8. Use Case Layer > Error Handling
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **UseCaseError**: Typed error results instead of exceptions
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 202. UseCaseResult: Success/failure result containers

- canonical title: UseCaseResult: Success/failure result containers
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 8. Use Case Layer > Error Handling
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **UseCaseResult**: Success/failure result containers
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 203. UserContext: Request-scoped user information

- canonical title: UserContext: Request-scoped user information
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 8. Use Case Layer > Error Handling
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **UserContext**: Request-scoped user information
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 204. Spotless: Palantir Java Format enforcement

- canonical title: Spotless: Palantir Java Format enforcement
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 10. Quality Gates and Standards > Code Quality Tools
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Spotless**: Palantir Java Format enforcement
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 205. Checkstyle: Code style validation

- canonical title: Checkstyle: Code style validation
- severity: unspecified
- category: data-integrity
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 10. Quality Gates and Standards > Code Quality Tools
- conflict/status note: Status signals: VALID.
- combined explanations:
  - - **Checkstyle**: Code style validation
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 206. PMD: Static analysis for bugs and maintainability

- canonical title: PMD: Static analysis for bugs and maintainability
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 10. Quality Gates and Standards > Code Quality Tools
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **PMD**: Static analysis for bugs and maintainability
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 207. JaCoCo: Test coverage measurement

- canonical title: JaCoCo: Test coverage measurement
- severity: unspecified
- category: testing
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 10. Quality Gates and Standards > Code Quality Tools
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **JaCoCo**: Test coverage measurement
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 208. Accessibility: Limited screen reader support

- canonical title: Accessibility: Limited screen reader support
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 11. Identified Gaps and Incomplete Features > UI/UX Gaps
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Accessibility**: Limited screen reader support
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 209. Documentation: API documentation could be more comprehensive

- canonical title: Documentation: API documentation could be more comprehensive
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 12. Code Quality Patterns > Areas for Improvement
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Documentation**: API documentation could be more comprehensive
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 210. Concurrency: Limited use of modern Java concurrency features

- canonical title: Concurrency: Limited use of modern Java concurrency features
- severity: unspecified
- category: concurrency
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 12. Code Quality Patterns > Areas for Improvement
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Concurrency**: Limited use of modern Java concurrency features
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 211. Performance: Some N+1 query patterns in storage layer

- canonical title: Performance: Some N+1 query patterns in storage layer
- severity: unspecified
- category: performance
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 12. Code Quality Patterns > Areas for Improvement
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Performance**: Some N+1 query patterns in storage layer
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 212. Authentication: No real authentication system (simulated sessions)

- canonical title: Authentication: No real authentication system (simulated sessions)
- severity: unspecified
- category: security
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 13. Security Considerations > Security Gaps
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Authentication**: No real authentication system (simulated sessions)
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 213. Authorization: Limited role-based access control

- canonical title: Authorization: Limited role-based access control
- severity: unspecified
- category: security
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 13. Security Considerations > Security Gaps
- conflict/status note: Status conflict: at least one source marks INVALID while others mark VALID/PARTIAL/RESOLVED.
- combined explanations:
  - - **Authorization**: Limited role-based access control - **Audit Logging**: Basic audit trails but limited security event logging - **Rate Limiting**: No API rate limiting implemented [INVALID] ❌ (Triple-verification confirms `LocalRateLimiter` exists in `RestApiServer.java`)
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 214. Caching: Image caching in UI layer

- canonical title: Caching: Image caching in UI layer
- severity: unspecified
- category: ui-ux
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 14. Performance Characteristics > Application Performance
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Caching**: Image caching in UI layer - **Async Operations**: Non-blocking UI operations - **Memory Management**: Efficient enum and collection usage
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 215. Cross-Platform: JavaFX supports Windows, macOS, Linux

- canonical title: Cross-Platform: JavaFX supports Windows, macOS, Linux
- severity: unspecified
- category: ui-ux
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 15. Deployment and Operations > Build Process
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Cross-Platform**: JavaFX supports Windows, macOS, Linux
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 216. Environment-Based: Environment variables for deployment config

- canonical title: Environment-Based: Environment variables for deployment config
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 15. Deployment and Operations > Configuration Management
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - - **Environment-Based**: Environment variables for deployment config - **JSON Defaults**: Sensible defaults with override capability
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 217. Validation: Startup-time configuration validation

- canonical title: Validation: Startup-time configuration validation
- severity: unspecified
- category: data-integrity
- mention_count_total: 2
- mention_count_by_file: CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md: 1, COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md: 1
- source_files:
  - CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md
  - COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md
- source_sections:
  - Comprehensive Codebase Analysis Report > Dating App - March 17, 2026 > 15. Deployment and Operations > Configuration Management
- conflict/status note: Status signals: VALID.
- combined explanations:
  - - **Validation**: Startup-time configuration validation
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 218. FI-AUD-001: Blocking does not update match state or conversation visibility. (RESOLVED 🟢)

- canonical title: FI-AUD-001: Blocking does not update match state or conversation visibility. (RESOLVED 🟢)
- severity: unspecified
- category: general
- mention_count_total: 2
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 2
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
  - Master Project Audit & Code Review Findings > 7. Addendum: Implementation Plan Fixes (2026-02-20)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-AUD-001**: Blocking does not update match state or conversation visibility.
  - | **FI-AUD-001**   | Block does not update match state or conversation visibility       | RESOLVED 🟢 | `TrustSafetyService` now accepts an optional `CommunicationStorage` (5-param constructor).
  - `updateMatchStateForBlock()` archives the conversation from the blocker's perspective (`MatchArchiveReason.BLOCK`) and sets `visibleToBlocker = false`.
  - `StorageFactory` passes `communicationStorage` to the service.
  - 4 new tests in `TrustSafetyServiceTest.BlockWithConversation`.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 219. Specific Missing Test Categories

- canonical title: Specific Missing Test Categories
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md: 1
- source_files:
  - CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md
- source_sections:
  - Dating App Codebase Investigation Report > Part 5: Test Coverage Gaps > Specific Missing Test Categories
- conflict/status note: Status conflict: at least one source marks INVALID while others mark VALID/PARTIAL/RESOLVED.
- combined explanations:
  - **AppSession** - Listener exception handling, concurrent listener modification 2.
  - **Event Bus** - Handler throwing in REQUIRED mode, event delivery during handler registration 3.
  - **API** - Malformed UUID, invalid enum values, request body size limits 4.
  - **Rate Limiter** - Concurrent access, window expiration timing 5.
  - **Configuration** - Weights exactly equaling 1.0, timezone parsing failures ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 220. Key Risks (Top Combined Priorities)

- canonical title: Key Risks (Top Combined Priorities)
- severity: unspecified
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.1 Key Risks (Top Combined Priorities)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - Data integrity can silently degrade due to foreign key constraints not being reliably applied.
  - The storage layer has no integration tests.
  - Blocking a user does not transition matches or messaging state.
  - Multiple features exist in core/CLI but are missing in JavaFX UI.
  - Documentation and issue trackers are out of sync with current code.
  - AppSession and related caching mechanisms exhibit memory leaks and thread-safety vulnerabilities.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 221. Issue Register (Actionable Findings)

- canonical title: Issue Register (Actionable Findings)
- severity: unspecified
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **Thread Safety & Concurrency**
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 222. FI-WKSP-001: `User` is mutable + shared via `AppSession`. (RESOLVED 🟢)

- canonical title: FI-WKSP-001: `User` is mutable + shared via `AppSession`. (RESOLVED 🟢)
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-WKSP-001**: `User` is mutable + shared via `AppSession`.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 223. FI-WKSP-002: `AppSession.setCurrentUser` race conditions. (RESOLVED 🟢)

- canonical title: FI-WKSP-002: `AppSession.setCurrentUser` race conditions. (RESOLVED 🟢)
- severity: unspecified
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-WKSP-002**: `AppSession.setCurrentUser` race conditions.
  - (RESOLVED 🟢) **Memory Leaks**
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 224. FI-WKSP-003: `SessionService.userLocks` grows unbounded. (RESOLVED 🟢)

- canonical title: FI-WKSP-003: `SessionService.userLocks` grows unbounded. (RESOLVED 🟢)
- severity: unspecified
- category: performance
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-WKSP-003**: `SessionService.userLocks` grows unbounded.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 225. FI-WKSP-004: `RecommendationService.cachedDailyPicks` grows unbounded. (RESOLVED 🟢)

- canonical title: FI-WKSP-004: `RecommendationService.cachedDailyPicks` grows unbounded. (RESOLVED 🟢)
- severity: unspecified
- category: performance
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-WKSP-004**: `RecommendationService.cachedDailyPicks` grows unbounded.
  - (RESOLVED 🟢) **Architecture & API Inconsistencies**
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 226. FI-WKSP-005: `UserStorage.get()` returns nullable User instead of Optional. (RESOLVED 🟢)

- canonical title: FI-WKSP-005: `UserStorage.get()` returns nullable User instead of Optional. (RESOLVED 🟢)
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-WKSP-005**: `UserStorage.get()` returns nullable User instead of Optional.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 227. FI-WKSP-006: `MessagingService.getMessages()` throws instead of returned Result. (VALID 🔴)

- canonical title: FI-WKSP-006: `MessagingService.getMessages()` throws instead of returned Result. (VALID 🔴)
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: VALID.
- combined explanations:
  - - **FI-WKSP-006**: `MessagingService.getMessages()` throws instead of returned Result.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 228. FI-WKSP-007: `MessagingService.sendMessage()` spurious `userStorage.save(sender)`. (VALID 🔴)

- canonical title: FI-WKSP-007: `MessagingService.sendMessage()` spurious `userStorage.save(sender)`. (VALID 🔴)
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: VALID.
- combined explanations:
  - - **FI-WKSP-007**: `MessagingService.sendMessage()` spurious `userStorage.save(sender)`.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 229. FI-WKSP-008: Match quality weights don't sum to 1.0. (VALID 🔴)

- canonical title: FI-WKSP-008: Match quality weights don't sum to 1.0. (VALID 🔴)
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: VALID.
- combined explanations:
  - - **FI-WKSP-008**: Match quality weights don't sum to 1.0.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 230. FI-WKSP-009: `TrustSafetyService` report auto-blocks and auto-bans in same call. (VALID 🔴)

- canonical title: FI-WKSP-009: `TrustSafetyService` report auto-blocks and auto-bans in same call. (VALID 🔴)
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: VALID.
- combined explanations:
  - - **FI-WKSP-009**: `TrustSafetyService` report auto-blocks and auto-bans in same call.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 231. FI-WKSP-010: `MessagingService.getConversations()` unbounded query. (RESOLVED 🟢)

- canonical title: FI-WKSP-010: `MessagingService.getConversations()` unbounded query. (RESOLVED 🟢)
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-WKSP-010**: `MessagingService.getConversations()` unbounded query.
  - (RESOLVED 🟢) **Feature Logic & UI Gaps**
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 232. FI-AUD-002: Conversation archive/visibility fields exist in schema but are unused by service/UI. (RESOLVED 🟢)

- canonical title: FI-AUD-002: Conversation archive/visibility fields exist in schema but are unused by service/UI. (RESOLVED 🟢)
- severity: unspecified
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-AUD-002**: Conversation archive/visibility fields exist in schema but are unused by service/UI.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 233. FI-AUD-003: Dashboard notifications and unread counts are defined but never populated. (RESOLVED 🟢)

- canonical title: FI-AUD-003: Dashboard notifications and unread counts are defined but never populated. (RESOLVED 🟢)
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-AUD-003**: Dashboard notifications and unread counts are defined but never populated.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 234. FI-AUD-004: Standouts are implemented in core and CLI but have no JavaFX UI. (RESOLVED 🟢)

- canonical title: FI-AUD-004: Standouts are implemented in core and CLI but have no JavaFX UI. (RESOLVED 🟢)
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-AUD-004**: Standouts are implemented in core and CLI but have no JavaFX UI.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 235. FI-AUD-005: Messaging “lastActiveAt” is not actually updated. (RESOLVED 🟢)

- canonical title: FI-AUD-005: Messaging “lastActiveAt” is not actually updated. (RESOLVED 🟢)
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-AUD-005**: Messaging “lastActiveAt” is not actually updated.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 236. FI-AUD-006: Age calculations use system default timezone instead of configured user timezone. (RESOLVED 🟢)

- canonical title: FI-AUD-006: Age calculations use system default timezone instead of configured user timezone. (RESOLVED 🟢)
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-AUD-006**: Age calculations use system default timezone instead of configured user timezone.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 237. FI-AUD-007: CSV serialization for preferences/sets limits queryability. (RESOLVED 🟢 - Pragmatic choice; formal array mapping adds unnecessary complexity for features not heavily queried.)

- canonical title: FI-AUD-007: CSV serialization for preferences/sets limits queryability. (RESOLVED 🟢 - Pragmatic choice; formal array mapping adds unnecessary complexity for features not heavily queried.)
- severity: unspecified
- category: performance
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-AUD-007**: CSV serialization for preferences/sets limits queryability.
  - (RESOLVED 🟢 - Pragmatic choice; formal array mapping adds unnecessary complexity for features not heavily queried.)
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 238. FI-AUD-008: UI only supports a single profile photo despite domain allowing two. (RESOLVED 🟢)

- canonical title: FI-AUD-008: UI only supports a single profile photo despite domain allowing two. (RESOLVED 🟢)
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-AUD-008**: UI only supports a single profile photo despite domain allowing two.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 239. FI-AUD-009: Standouts data has no cleanup path and can grow unbounded. (RESOLVED 🟢)

- canonical title: FI-AUD-009: Standouts data has no cleanup path and can grow unbounded. (RESOLVED 🟢)
- severity: unspecified
- category: performance
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-AUD-009**: Standouts data has no cleanup path and can grow unbounded.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 240. FI-AUD-010: Messaging pagination parameters are not validated. (RESOLVED 🟢)

- canonical title: FI-AUD-010: Messaging pagination parameters are not validated. (RESOLVED 🟢)
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status conflict: one or more sources mark RESOLVED while others still mark active/partial.
- combined explanations:
  - - **FI-AUD-010**: Messaging pagination parameters are not validated.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 241. FI-AUD-011: Configuration documentation mismatch for DB password. (RESOLVED 🟢)

- canonical title: FI-AUD-011: Configuration documentation mismatch for DB password. (RESOLVED 🟢)
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.2 Issue Register (Actionable Findings)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - - **FI-AUD-011**: Configuration documentation mismatch for DB password.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 242. Unimplemented Or Partial Features (Cross-Layer Gaps)

- canonical title: Unimplemented Or Partial Features (Cross-Layer Gaps)
- severity: unspecified
- category: security
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.3 Unimplemented Or Partial Features (Cross-Layer Gaps)
- conflict/status note: Status conflict: one or more sources mark RESOLVED while others still mark active/partial.
- combined explanations:
  - *(All VALID 🟢 unless noted)* 1.
  - JavaFX UI for trust & safety actions (block, report, verify) exists in CLI only.
  - (RESOLVED 🟢 — Block/Report dialogs added to `MatchingController` and `ChatController`; delegates to `MatchingViewModel.blockCandidate/reportCandidate` and `ChatViewModel.blockUser/reportUser`.) 2.
  - JavaFX UI for friend requests and notifications exists in CLI only.
  - (RESOLVED 🟢 — `SocialViewModel`, `SocialController`, and `social.fxml` implemented; tabbed view with Notifications + Friend Requests tabs wired to `ConnectionService` and `UiSocialDataAccess` adapter.
  - Dashboard Social card navigates here.) 3.
  - JavaFX UI for profile notes exists in CLI only.
  - (RESOLVED 🟢 — `NotesViewModel`, `NotesController`, and `notes.fxml` now provide a dedicated notes browser, and note editing is also available inline from Matching and Chat.) 4.
  - JavaFX UI for standouts exists in CLI only.
  - (RESOLVED 🟢 — `StandoutsViewModel`, `StandoutsController`, and `standouts.fxml` implemented; loads from `RecommendationService`, resolves users, exposes `StandoutEntry` records.
  - Dashboard Standouts card navigates here.) 5.
  - Authentication beyond user selection is not implemented.
  - Real-time chat (push or WebSocket) is not implemented; UI relies on manual refresh.
  - Read receipts are stored but not surfaced in UI.
  - Multi-photo profile gallery is not implemented in UI.
  - Notification counts on dashboard are defined but not wired.
  - REST API layer is not implemented (Wait, `RestApiServer.java` is implemented now, so RESOLVED 🟢).
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 243. Test And Quality Gaps

- canonical title: Test And Quality Gaps
- severity: unspecified
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.4 Test And Quality Gaps
- conflict/status note: Status conflict: one or more sources mark RESOLVED while others still mark active/partial.
- combined explanations:
  - Storage layer has zero integration tests.
  - StandoutsService and JdbiStandoutStorage have no dedicated tests.
  - (RESOLVED 🟢 - `StandoutsServiceTest.java` exists) 3.
  - Social storage (friend requests/notifications) has no integration tests.
  - UI controllers are mostly untested beyond CSS validation.
  - (PARTIAL 🟡 — controller tests now cover Chat, Matching, Matches, Profile, Preferences, Milestone popup, Notes, and Safety screens; some screens still have lighter direct coverage.) 5.
  - Threading and race-condition tests for viewmodels are absent.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 244. Documentation Drift And Process Debt

- canonical title: Documentation Drift And Process Debt
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 2. Project & Workspace Audit: Key Risks & Gaps > 2.5 Documentation Drift And Process Debt
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - Multiple issue tracking docs in `docs/uncompleted-plans` appear stale.
  - (RESOLVED 🟢) **Document Drift and Configuration Status:** - `DatabaseManager.java` manages H2 connections.
  - For production/standalone mode, it requires the `DATING_APP_DB_PASSWORD` environment variable.
  - - For local file databases (`jdbc:h2:./...`), it auto-defaults to `dev`.
  - - For in-memory testing (`jdbc:h2:mem:...`), it auto-defaults to `""`.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 245. Summary: `DatabaseManager.java` contained massive embedded SQL DDL strings.

- canonical title: Summary: `DatabaseManager.java` contained massive embedded SQL DDL strings.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.2 Code Architecture & Organization > FI-CONS-001: God Object - DatabaseManager
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: `DatabaseManager.java` contained massive embedded SQL DDL strings.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 246. Resolution: Extracted DDL into `SchemaInitializer.java` and standard schema resources.

- canonical title: Resolution: Extracted DDL into `SchemaInitializer.java` and standard schema resources.
- severity: unspecified
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.2 Code Architecture & Organization > FI-CONS-001: God Object - DatabaseManager
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Resolution**: Extracted DDL into `SchemaInitializer.java` and standard schema resources.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 247. Summary: `ProfileController` is a "God Controller" mixing event handling, complex styling logic, and redundant Enum-to-String converter factories.

- canonical title: Summary: `ProfileController` is a "God Controller" mixing event handling, complex styling logic, and redundant Enum-to-String converter factories.
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.2 Code Architecture & Organization > FI-CONS-002: UI Controller Bloat - ProfileController
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: `ProfileController` is a "God Controller" mixing event handling, complex styling logic, and redundant Enum-to-String converter factories.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 248. Recommendation: Extract UI utility methods to a `UiUtils` class. Decomposition into sub-controllers or components is recommended.

- canonical title: Recommendation: Extract UI utility methods to a `UiUtils` class. Decomposition into sub-controllers or components is recommended.
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.2 Code Architecture & Organization > FI-CONS-002: UI Controller Bloat - ProfileController
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Recommendation**: Extract UI utility methods to a `UiUtils` class.
  - Decomposition into sub-controllers or components is recommended.
- combined recommendations:
  - *   **Recommendation**: Extract UI utility methods to a `UiUtils` class. Decomposition into sub-controllers or components is recommended.
- evidence quote snippets:

## 249. Summary: The `User` class was excessively large, partly due to the inclusion of the complex `ProfileNote` record as a nested entity.

- canonical title: Summary: The `User` class was excessively large, partly due to the inclusion of the complex `ProfileNote` record as a nested entity.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.2 Code Architecture & Organization > FI-CONS-003: Core Entity Bloat - User.java
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: The `User` class was excessively large, partly due to the inclusion of the complex `ProfileNote` record as a nested entity.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 250. Resolution: `ProfileNote` was promoted to a standalone top-level record at `core/model/ProfileNote.java`. It is also re-exported as `User.ProfileNote` (public static nested) per the 2026-02-19 re-nesting convention so existing import paths remain valid.

- canonical title: Resolution: `ProfileNote` was promoted to a standalone top-level record at `core/model/ProfileNote.java`. It is also re-exported as `User.ProfileNote` (public static nested) per the 2026-02-19 re-nesting convention so existing import paths remain valid.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.2 Code Architecture & Organization > FI-CONS-003: Core Entity Bloat - User.java
- conflict/status note: Status signals: VALID.
- combined explanations:
  - *   **Resolution**: `ProfileNote` was promoted to a standalone top-level record at `core/model/ProfileNote.java`.
  - It is also re-exported as `User.ProfileNote` (public static nested) per the 2026-02-19 re-nesting convention so existing import paths remain valid.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 251. Summary: Validation rules were duplicated between domain setters and the validation layer.

- canonical title: Summary: Validation rules were duplicated between domain setters and the validation layer.
- severity: unspecified
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.3 Redundancy & Duplication > FI-CONS-004: Duplicated Validation Logic
- conflict/status note: Status signals: VALID.
- combined explanations:
  - *   **Summary**: Validation rules were duplicated between domain setters and the validation layer.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 252. Resolution: Domain setters in `User.java` no longer hardcode or duplicate these rules (e.g. `minAge >= 18`), deferring entirely to `ValidationService.java` which acts as the sole validator using `AppConfig.defaults()`.

- canonical title: Resolution: Domain setters in `User.java` no longer hardcode or duplicate these rules (e.g. `minAge >= 18`), deferring entirely to `ValidationService.java` which acts as the sole validator using `AppConfig.defaults()`.
- severity: unspecified
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.3 Redundancy & Duplication > FI-CONS-004: Duplicated Validation Logic
- conflict/status note: Status signals: VALID.
- combined explanations:
  - *   **Resolution**: Domain setters in `User.java` no longer hardcode or duplicate these rules (e.g.
  - `minAge >= 18`), deferring entirely to `ValidationService.java` which acts as the sole validator using `AppConfig.defaults()`.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 253. Summary: Many test classes defined their own private in-memory storage stubs.

- canonical title: Summary: Many test classes defined their own private in-memory storage stubs.
- severity: unspecified
- category: testing
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.3 Redundancy & Duplication > FI-CONS-005: Proliferation of Test Stubs
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: Many test classes defined their own private in-memory storage stubs.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 254. Resolution: Consolidated stubs into `TestStorages.java` which is heavily used across the suite.

- canonical title: Resolution: Consolidated stubs into `TestStorages.java` which is heavily used across the suite.
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.3 Redundancy & Duplication > FI-CONS-005: Proliferation of Test Stubs
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Resolution**: Consolidated stubs into `TestStorages.java` which is heavily used across the suite.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 255. Summary: `theme.css` contained redundant rules and hardcoded hex colors.

- canonical title: Summary: `theme.css` contained redundant rules and hardcoded hex colors.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.3 Redundancy & Duplication > FI-CONS-006: CSS Redundancy & Hardcoded Colors
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: `theme.css` contained redundant rules and hardcoded hex colors.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 256. Resolution: Replaced with theme variables.

- canonical title: Resolution: Replaced with theme variables.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.3 Redundancy & Duplication > FI-CONS-006: CSS Redundancy & Hardcoded Colors
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Resolution**: Replaced with theme variables.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 257. Summary: Used `catch (Exception _)` during match saving.

- canonical title: Summary: Used `catch (Exception _)` during match saving.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.4 Technical Debt & Smells > FI-CONS-007: Broad Exception Catching - MatchingService
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: Used `catch (Exception _)` during match saving.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 258. Resolution: Changed to `catch (RuntimeException _)`.

- canonical title: Resolution: Changed to `catch (RuntimeException _)`.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.4 Technical Debt & Smells > FI-CONS-007: Broad Exception Catching - MatchingService
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Resolution**: Changed to `catch (RuntimeException _)`.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 259. Summary: Massive blocks of manual wiring in registries.

- canonical title: Summary: Massive blocks of manual wiring in registries.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.4 Technical Debt & Smells > FI-CONS-008: Manual Dependency Injection Boilerplate
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: Massive blocks of manual wiring in registries.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 260. Resolution: Organized with clear structured blocks.

- canonical title: Resolution: Organized with clear structured blocks.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.4 Technical Debt & Smells > FI-CONS-008: Manual Dependency Injection Boilerplate
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Resolution**: Organized with clear structured blocks.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 261. Summary: 44+ `TODO` comments were scattered across source files.

- canonical title: Summary: 44+ `TODO` comments were scattered across source files.
- severity: unspecified
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.4 Technical Debt & Smells > FI-CONS-009: Scattered TODOs
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: 44+ `TODO` comments were scattered across source files.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 262. Resolution: Source code has zero TODOs remaining.

- canonical title: Resolution: Source code has zero TODOs remaining.
- severity: unspecified
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.4 Technical Debt & Smells > FI-CONS-009: Scattered TODOs
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Resolution**: Source code has zero TODOs remaining.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 263. Resolution: Current project guidance is to use injected runtime config in production code and reserve `AppConfig.defaults()` mainly for bootstrap, composition, and tests.

- canonical title: Resolution: Current project guidance is to use injected runtime config in production code and reserve `AppConfig.defaults()` mainly for bootstrap, composition, and tests.
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.5 Configuration & Constants > FI-CONS-010: Fragmented Configuration Constants / Inconsistent Sourcing
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Resolution**: Current project guidance is to use injected runtime config in production code and reserve `AppConfig.defaults()` mainly for bootstrap, composition, and tests.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 264. Status: PARTIAL / VALID 🟢

- canonical title: Status: PARTIAL / VALID 🟢
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.6 Storage & JDBI Optimization > FI-CONS-011: JDBI SQL Redundancy & Interface Bloat
- conflict/status note: Status conflict: one or more sources mark RESOLVED while others still mark active/partial.
- combined explanations:
  - *   **Status**: PARTIAL / VALID 🟢
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 265. Summary: JDBI storage mappers contain repetitive SQL column lists.

- canonical title: Summary: JDBI storage mappers contain repetitive SQL column lists.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.6 Storage & JDBI Optimization > FI-CONS-011: JDBI SQL Redundancy & Interface Bloat
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: JDBI storage mappers contain repetitive SQL column lists.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 266. Resolution: Extracted `ALL_COLUMNS` constant in some places, but still contains redundancy.

- canonical title: Resolution: Extracted `ALL_COLUMNS` constant in some places, but still contains redundancy.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.6 Storage & JDBI Optimization > FI-CONS-011: JDBI SQL Redundancy & Interface Bloat
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Resolution**: Extracted `ALL_COLUMNS` constant in some places, but still contains redundancy.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 267. Status: VALID 🟡 (Pragmatic Choice)

- canonical title: Status: VALID 🟡 (Pragmatic Choice)
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.6 Storage & JDBI Optimization > FI-CONS-012: Inefficient Complex Type Serialization
- conflict/status note: Status signals: PARTIAL, VALID.
- combined explanations:
  - *   **Status**: VALID 🟡 (Pragmatic Choice)
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 268. Summary: CSV serialization is fragile.

- canonical title: Summary: CSV serialization is fragile.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.6 Storage & JDBI Optimization > FI-CONS-012: Inefficient Complex Type Serialization
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: CSV serialization is fragile.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 269. Recommendation: Consider JDBI JSON or H2 arrays. Currently accepted as adequate.

- canonical title: Recommendation: Consider JDBI JSON or H2 arrays. Currently accepted as adequate.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.6 Storage & JDBI Optimization > FI-CONS-012: Inefficient Complex Type Serialization
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Recommendation**: Consider JDBI JSON or H2 arrays.
  - Currently accepted as adequate.
- combined recommendations:
  - *   **Recommendation**: Consider JDBI JSON or H2 arrays. Currently accepted as adequate.
- evidence quote snippets:

## 270. Summary: Complex logic in CandidateFinder.

- canonical title: Summary: Complex logic in CandidateFinder.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.6 Storage & JDBI Optimization > FI-CONS-013: Over-engineered Stateless Algorithms
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: Complex logic in CandidateFinder.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 271. Resolution: Actually well-structured and unit-tested; no simplification needed.

- canonical title: Resolution: Actually well-structured and unit-tested; no simplification needed.
- severity: unspecified
- category: testing
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.6 Storage & JDBI Optimization > FI-CONS-013: Over-engineered Stateless Algorithms
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Resolution**: Actually well-structured and unit-tested; no simplification needed.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 272. Summary: Docs referenced nested storage interfaces instead of standalone.

- canonical title: Summary: Docs referenced nested storage interfaces instead of standalone.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.6 Storage & JDBI Optimization > FI-CONS-014: Documentation Drift
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Summary**: Docs referenced nested storage interfaces instead of standalone.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 273. Resolution: Documentation synced with reality.

- canonical title: Resolution: Documentation synced with reality.
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 3. Categorized Code & Architecture Review Findings > 3.6 Storage & JDBI Optimization > FI-CONS-014: Documentation Drift
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - *   **Resolution**: Documentation synced with reality.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 274. FI-ARCH-001: Layer boundaries bypassed

- canonical title: FI-ARCH-001: Layer boundaries bypassed
- severity: unspecified
- category: security
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status conflict: one or more sources mark RESOLVED while others still mark active/partial.
- combined explanations:
  - | **FI-ARCH-001** | Layer boundaries bypassed          | VALID 🟢                 | Architecture Review |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 275. FI-ARCH-002: Global mutable singletons

- canonical title: FI-ARCH-002: Global mutable singletons
- severity: unspecified
- category: architecture
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status conflict: one or more sources mark RESOLVED while others still mark active/partial.
- combined explanations:
  - | **FI-ARCH-002** | Global mutable singletons          | VALID 🟢                 | Architecture Review |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 276. FI-ARCH-003: Partially constructible services

- canonical title: FI-ARCH-003: Partially constructible services
- severity: unspecified
- category: architecture
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status signals: PARTIAL.
- combined explanations:
  - | **FI-ARCH-003** | Partially constructible services   | PARTIAL 🟡               | Architecture Review |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 277. FI-ARCH-004: Large multi-responsibility units

- canonical title: FI-ARCH-004: Large multi-responsibility units
- severity: unspecified
- category: architecture
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status conflict: one or more sources mark RESOLVED while others still mark active/partial.
- combined explanations:
  - | **FI-ARCH-004** | Large multi-responsibility units   | VALID 🟢                 | Architecture Review |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 278. FI-CONS-001: DatabaseManager (God Object)

- canonical title: FI-CONS-001: DatabaseManager (God Object)
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-CONS-001** | DatabaseManager (God Object)       | RESOLVED 🟢              | Code Review         |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 279. FI-CONS-002: ProfileController (Bloat)

- canonical title: FI-CONS-002: ProfileController (Bloat)
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status conflict: one or more sources mark RESOLVED while others still mark active/partial.
- combined explanations:
  - | **FI-CONS-002** | ProfileController (Bloat)          | VALID 🟢                 | Code Review         |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 280. FI-CONS-003: User.java (Sprawl) / Nested logic

- canonical title: FI-CONS-003: User.java (Sprawl) / Nested logic
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-CONS-003** | User.java (Sprawl) / Nested logic  | RESOLVED 🟢              | Code Review         |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 281. FI-CONS-004: Duplicated Validation

- canonical title: FI-CONS-004: Duplicated Validation
- severity: unspecified
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status conflict: one or more sources mark RESOLVED while others still mark active/partial.
- combined explanations:
  - | **FI-CONS-004** | Duplicated Validation              | RESOLVED 🟢              | Code Review         |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 282. FI-CONS-005: Test Stub Proliferation

- canonical title: FI-CONS-005: Test Stub Proliferation
- severity: unspecified
- category: testing
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-CONS-005** | Test Stub Proliferation            | RESOLVED 🟢              | Code Review         |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 283. FI-CONS-006: CSS Redundancy

- canonical title: FI-CONS-006: CSS Redundancy
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-CONS-006** | CSS Redundancy                     | RESOLVED 🟢              | Code Review         |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 284. FI-CONS-007: MatchingService Exception Handling

- canonical title: FI-CONS-007: MatchingService Exception Handling
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-CONS-007** | MatchingService Exception Handling | RESOLVED 🟢              | Code Review         |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 285. FI-CONS-008: Manual DI Boilerplate

- canonical title: FI-CONS-008: Manual DI Boilerplate
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-CONS-008** | Manual DI Boilerplate              | RESOLVED 🟢              | Code Review         |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 286. FI-CONS-009: Scattered TODOs

- canonical title: FI-CONS-009: Scattered TODOs
- severity: unspecified
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-CONS-009** | Scattered TODOs                    | RESOLVED 🟢              | Code Review         |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 287. FI-CONS-010: Fragmented / Inconsistent Config

- canonical title: FI-CONS-010: Fragmented / Inconsistent Config
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status conflict: at least one source marks INVALID while others mark VALID/PARTIAL/RESOLVED.
- combined explanations:
  - | **FI-CONS-010** | Fragmented / Inconsistent Config   | RESOLVED 🟢 / INVALID 🔴 | Both                |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 288. FI-CONS-011: JDBI SQL Redundancy

- canonical title: FI-CONS-011: JDBI SQL Redundancy
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status signals: PARTIAL.
- combined explanations:
  - | **FI-CONS-011** | JDBI SQL Redundancy                | PARTIAL 🟡               | Code Review         |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 289. FI-CONS-012: Inefficient Serialization

- canonical title: FI-CONS-012: Inefficient Serialization
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status signals: PARTIAL, VALID.
- combined explanations:
  - | **FI-CONS-012** | Inefficient Serialization          | VALID (Pragmatic) 🟡     | Code Review         |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 290. FI-CONS-013: Algorithmic Over-engineering

- canonical title: FI-CONS-013: Algorithmic Over-engineering
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status conflict: at least one source marks INVALID while others mark VALID/PARTIAL/RESOLVED.
- combined explanations:
  - | **FI-CONS-013** | Algorithmic Over-engineering       | INVALID 🔴               | Code Review         |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 291. FI-CONS-014: Documentation Drift

- canonical title: FI-CONS-014: Documentation Drift
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 5. Appendix: Consolidated Mapping of Original IDs
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-CONS-014** | Documentation Drift                | RESOLVED 🟢              | Code Review         |
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 292. FI-WKSP-006: `getMessages` Exception signature

- canonical title: FI-WKSP-006: `getMessages` Exception signature
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 6. Addendum: Workspace Audit Fixes (Feb 2026 Phase)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-WKSP-006** | `getMessages` Exception signature                 | RESOLVED 🟢 | Wrapped response in `MessageLoadResult` record.
  - Call-sites (API, CLI, JavaFX) updated.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 293. FI-WKSP-007: `sendMessage` spurious `user.save`

- canonical title: FI-WKSP-007: `sendMessage` spurious `user.save`
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 6. Addendum: Workspace Audit Fixes (Feb 2026 Phase)
- conflict/status note: Status conflict: at least one source marks INVALID while others mark VALID/PARTIAL/RESOLVED.
- combined explanations:
  - | **FI-WKSP-007** | `sendMessage` spurious `user.save`                | INVALID 🔴  | Code review confirms this was already removed in the current codebase state.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 294. FI-WKSP-008: Match weights scale to 1.0 mismatch

- canonical title: FI-WKSP-008: Match weights scale to 1.0 mismatch
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 6. Addendum: Workspace Audit Fixes (Feb 2026 Phase)
- conflict/status note: Status conflict: at least one source marks INVALID while others mark VALID/PARTIAL/RESOLVED.
- combined explanations:
  - | **FI-WKSP-008** | Match weights scale to 1.0 mismatch               | INVALID 🔴  | `AppConfig` weights literally sum to `1.0` exactly (`0.15 + 0.10 + 0.25 + 0.25 + 0.15 + 0.10`).
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 295. FI-WKSP-009: Duplicated Auto-block/Ban in `TrustSafetyService`

- canonical title: FI-WKSP-009: Duplicated Auto-block/Ban in `TrustSafetyService`
- severity: unspecified
- category: general
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 6. Addendum: Workspace Audit Fixes (Feb 2026 Phase)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-WKSP-009** | Duplicated Auto-block/Ban in `TrustSafetyService` | RESOLVED 🟢 | Refactored `report()` to conditionally branch blocking only if `applyAutoBanIfThreshold` returns false.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 296. FI-AUD-002: Archive/visibility fields unused by service/UI

- canonical title: FI-AUD-002: Archive/visibility fields unused by service/UI
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 7. Addendum: Implementation Plan Fixes (2026-02-20)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-AUD-002**   | Archive/visibility fields unused by service/UI                     | RESOLVED 🟢 | Fields are now fully used: `ConnectionService.unmatch()` archives both sides with `MatchArchiveReason.UNMATCH`; `TrustSafetyService.block()` archives the blocker's side with `MatchArchiveReason.BLOCK` and hides it.
  - `MatchingHandler` routes unmatch through `ConnectionService.unmatch()` instead of directly manipulating the `Match` entity.
  - 5 new tests in `MessagingServiceTest.Unmatch`.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 297. FI-AUD-005: Messaging `lastActiveAt` not updated

- canonical title: FI-AUD-005: Messaging `lastActiveAt` not updated
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 7. Addendum: Implementation Plan Fixes (2026-02-20)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-AUD-005**   | Messaging `lastActiveAt` not updated                               | RESOLVED 🟢 | Pre-existing: `ConnectionService.sendMessage()` already called `activityMetricsService.recordActivity()` (when non-null).
  - `ActivityMetricsService.recordActivity()` updates session `lastActivityAt`.
  - Confirmed in code review — no change required.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 298. FI-AUD-006: Age uses system timezone instead of config timezone

- canonical title: FI-AUD-006: Age uses system timezone instead of config timezone
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 7. Addendum: Implementation Plan Fixes (2026-02-20)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-AUD-006**   | Age uses system timezone instead of config timezone                | RESOLVED 🟢 | Pre-existing: `User.getAge()` already calls `AppConfig.defaults().safety().userTimeZone()` for the timezone.
  - Confirmed in code review — no change required.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 299. FI-AUD-009: Standouts data has no cleanup path

- canonical title: FI-AUD-009: Standouts data has no cleanup path
- severity: unspecified
- category: testing
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 7. Addendum: Implementation Plan Fixes (2026-02-20)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-AUD-009**   | Standouts data has no cleanup path                                 | RESOLVED 🟢 | Pre-existing: `ActivityMetricsService.runCleanup()` already called `analyticsStorage.deleteExpiredStandouts()`.
  - `CleanupResult` now also reports `standoutsDeleted` in its `toString()` (field was missing).
  - Fixed pre-existing bug where `CleanupResult.toString()` omitted `standoutsDeleted` and used abbreviated field names, causing a test assertion failure.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 300. FI-CONS-003: `ProfileNote` nested in `User` causes bloat

- canonical title: FI-CONS-003: `ProfileNote` nested in `User` causes bloat
- severity: unspecified
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 7. Addendum: Implementation Plan Fixes (2026-02-20)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-CONS-003**  | `ProfileNote` nested in `User` causes bloat                        | RESOLVED 🟢 | `ProfileNote` was extracted to a standalone top-level record at `core/model/ProfileNote.java`.
  - Also re-exported as `User.ProfileNote` (public static nested) per the 2026-02-19 re-nesting convention for import-path compatibility.
  - | | *(pre-existing)* | `JdbiConnectionStorage.ConversationMapper` read stale column names | RESOLVED 🟢 | Mapper was reading obsolete `archived_at`/`archive_reason` columns instead of the split-schema aliases `user_a_archived_at`, `user_a_archive_reason`, `user_b_archived_at`, `user_b_archive_reason`.
  - Fixed to read all 4 aliases and call the 13-param `Conversation` constructor.
  - Root cause of 3 `MessagingHandlerTest` failures.
  - | | *(pre-existing)* | `RelationshipTransitionServiceTest` used deleted accessor methods  | RESOLVED 🟢 | Test called `getArchivedAt()`/`getArchiveReason()` which were removed when the schema was split into per-user fields.
  - Updated to assert `getUserAArchivedAt()`, `getUserBArchivedAt()`, `getUserAArchiveReason()`, `getUserBArchiveReason()`.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 301. FI-AUD-004: Standouts UI missing in JavaFX

- canonical title: FI-AUD-004: Standouts UI missing in JavaFX
- severity: unspecified
- category: concurrency
- mention_count_total: 1
- mention_count_by_file: MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md: 1
- source_files:
  - MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md
- source_sections:
  - Master Project Audit & Code Review Findings > 8. Addendum: UI Feature Parity Implementation (2026-02-21)
- conflict/status note: Status signals: RESOLVED.
- combined explanations:
  - | **FI-AUD-004**           | Standouts UI missing in JavaFX                                            | RESOLVED 🟢 | `StandoutsViewModel` (`ui/viewmodel/`) loads standouts via `RecommendationService.getStandouts()` + `resolveUsers()` on a virtual thread, exposes `ObservableList<StandoutEntry>` (inner record combining `Standout` + resolved `User`).
  - `StandoutsController` (`ui/screen/`) wires list selection to `markInteracted()` + navigation to Matching.
  - `standouts.fxml` provides header with Back/Refresh + ranked list.
  - Dashboard Standouts card + `NavigationService.ViewType.STANDOUTS` wired.
  - | | **FI-WKSP-001 (UI)**     | Trust & safety block/report missing from JavaFX Matching and Chat screens | RESOLVED 🟢 | Block and Report buttons added to `matching.fxml` (over candidate card) and `chat.fxml` (conversation action bar).
  - `MatchingController.handleBlock/handleReport` delegates to `MatchingViewModel.blockCandidate/reportCandidate`.
  - `ChatController.handleBlock/handleReport` delegates to `ChatViewModel.blockUser/reportUser`.
  - Both use a `Dialog<Report.Reason>` with `UiUtils.createEnumStringConverter()` for clean enum display.
  - | | **2.3 item 2**           | Social (friend requests + notifications) UI missing in JavaFX             | RESOLVED 🟢 | `UiSocialDataAccess` adapter interface + `StorageUiSocialDataAccess` impl added to `UiDataAdapters.java` (wraps `CommunicationStorage`).
  - `SocialViewModel` loads notifications and pending friend requests on virtual threads, resolves sender names via `UiUserStore.findByIds()` batch lookup, exposes `FriendRequestEntry` inner record with `fromUserName`.
  - `SocialController` renders a two-tab `TabPane` (Notifications + Friend Requests).
  - Dashboard Social card + `NavigationService.ViewType.SOCIAL` wired.
  - Accept/Decline delegates to `ConnectionService.acceptFriendZone/declineFriendZone`.
  - | | **2.4 item 5 (new VMs)** | ViewModel threading tests for new screens absent                          | RESOLVED 🟢 | `StandoutsViewModelTest` (5 tests): pre-seeded standout storage for determinism, verifies async load, `StandoutEntry` delegation, dispose-prevents-update, `markInteracted` idempotency.
  - `SocialViewModelTest` (7 tests): notification load, friend-request name resolution, UUID fallback for unresolvable sender, `markNotificationRead`, already-read no-op, concurrent refresh safety, dispose clears both lists.
  - | | *(pre-existing)*         | `ChatViewModelTest` race condition and non-deterministic ordering         | RESOLVED 🟢 | Two pre-existing bugs: (1) `setCurrentUser()` triggering an async refresh in setUp could post a stale `updateConversations([])` after the test's own refresh — fixed by adding `Thread.sleep(300)` + FX drain at end of setUp.
  - (2) Conversations with equal `lastMessageAt` (fixed clock) had non-deterministic index order — fixed by looking up conversations by `otherUser.getId()` instead of assuming `get(0)`/`get(1)` positions.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 302. JavaFX GUI (`datingapp.ui`)

- canonical title: JavaFX GUI (`datingapp.ui`)
- severity: unspecified
- category: ui-ux
- mention_count_total: 1
- mention_count_by_file: STATUS_2026-03-09.md: 1
- source_files:
  - STATUS_2026-03-09.md
- source_sections:
  - Current System Status > 5. Presentation Layers > JavaFX GUI (`datingapp.ui`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - Implements the MVVM (Model-View-ViewModel) pattern, with `ViewModelFactory` handling injection.
  - Currently features 10 active controllers (e.g., `ProfileController`, `MatchingController`, `ChatController`, `DashboardController`, `MilestonePopupController`).
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 303. CLI Console (`datingapp.app.cli`)

- canonical title: CLI Console (`datingapp.app.cli`)
- severity: unspecified
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: STATUS_2026-03-09.md: 1
- source_files:
  - STATUS_2026-03-09.md
- source_sections:
  - Current System Status > 5. Presentation Layers > CLI Console (`datingapp.app.cli`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - Acts as a fallback or debugging interface with 5 primary handlers: *   `MatchingHandler` *   `ProfileHandler` *   `MessagingHandler` *   `SafetyHandler` *   `StatsHandler`
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 304. REST API (`datingapp.app.api`)

- canonical title: REST API (`datingapp.app.api`)
- severity: unspecified
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: STATUS_2026-03-09.md: 1
- source_files:
  - STATUS_2026-03-09.md
- source_sections:
  - Current System Status > 5. Presentation Layers > REST API (`datingapp.app.api`)
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - A single Javalin server implementation (`RestApiServer.java`) providing HTTP endpoints for client consumption.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:

## 305. MatchPreferences — Convenience Dealbreaker Methods Not Removed

- canonical title: MatchPreferences — Convenience Dealbreaker Methods Not Removed
- severity: unspecified
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: STATUS_2026-03-17_By_claude_sonnet_4.6.md: 1
- source_files:
  - STATUS_2026-03-17_By_claude_sonnet_4.6.md
- source_sections:
  - Project Status & Gap Analysis — 2026-03-17 > Code Quality Issues Found in Source > 1. MatchPreferences — Convenience Dealbreaker Methods Not Removed
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `src/main/java/datingapp/core/profile/MatchPreferences.java` lines 447–473 The refactoring plan (Task 2) explicitly said: *"Remove `hasSmokingDealbreaker()` etc.
  - They still exist: `hasSmokingDealbreaker()`, `hasDrinkingDealbreaker()`, `hasKidsDealbreaker()`, `hasLookingForDealbreaker()`, `hasEducationDealbreaker()`, `hasHeightDealbreaker()`, `hasAgeDealbreaker()`.
  - The loop-based evaluation was added correctly, but the old convenience methods were not pruned.
  - Minor issue — they don't break anything but contradict the plan.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - **File:** `src/main/java/datingapp/core/profile/MatchPreferences.java` lines 447–473

## 306. `ProfileService.legacyAchievementService()` — Instantiation Anti-Pattern

- canonical title: `ProfileService.legacyAchievementService()` — Instantiation Anti-Pattern
- severity: unspecified
- category: feature-gap
- mention_count_total: 1
- mention_count_by_file: STATUS_2026-03-17_By_claude_sonnet_4.6.md: 1
- source_files:
  - STATUS_2026-03-17_By_claude_sonnet_4.6.md
- source_sections:
  - Project Status & Gap Analysis — 2026-03-17 > Code Quality Issues Found in Source > 2. `ProfileService.legacyAchievementService()` — Instantiation Anti-Pattern
- conflict/status note: No explicit status markers found across sources.
- combined explanations:
  - **File:** `src/main/java/datingapp/core/profile/ProfileService.java` lines 185–188   Called by **5 methods** (`checkAndUnlock`, `getUnlocked`, `getProgress`, `getProgressByCategory`, `countUnlocked`).
  - Creates a brand-new `DefaultAchievementService` on every invocation.
  - Should be initialized once as a `final` field in the constructor.
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - java
private DefaultAchievementService legacyAchievementService() {
    return new DefaultAchievementService(
            config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage, this);
}
  - **File:** `src/main/java/datingapp/core/profile/ProfileService.java` lines 185–188

## 307. AppConfig.Builder — 447 LOC to Delete

- canonical title: AppConfig.Builder — 447 LOC to Delete
- severity: unspecified
- category: data-integrity
- mention_count_total: 1
- mention_count_by_file: STATUS_2026-03-17_By_claude_sonnet_4.6.md: 1
- source_files:
  - STATUS_2026-03-17_By_claude_sonnet_4.6.md
- source_sections:
  - Project Status & Gap Analysis — 2026-03-17 > Code Quality Issues Found in Source > 3. AppConfig.Builder — 447 LOC to Delete
- conflict/status note: Status signals: VALID.
- combined explanations:
  - **File:** `src/main/java/datingapp/core/AppConfig.java` - Total: **672 lines** - `public static AppConfig defaults()` at line 210 — currently calls `builder().build()`, i.e.
  - still uses the Builder - Builder class starts at **line 224**, ends at **line 670** — 447 lines - **None** of the four sub-records (`MatchingConfig`, `ValidationConfig`, `AlgorithmConfig`, `SafetyConfig`) have `defaults()` factory methods yet - The Builder's `build()` method delegates to `buildMatchingConfig()`, `buildValidationConfig()`, `buildAlgorithmConfig()`, `buildSafetyConfig()` — these become the templates for the new `defaults()` methods **To complete Task 3:** 1.
  - Add `defaults()` to each sub-record, extracting values from the corresponding `buildXxxConfig()` builder method 2.
  - Change `AppConfig.defaults()` to construct sub-records directly: `return new AppConfig(MatchingConfig.defaults(), ...)` 3.
  - Delete lines 224–670 (the entire Builder class) ---
- combined recommendations:
  - (No explicit recommendation text captured; recommendation inferred from source issue context.)
- evidence quote snippets:
  - - Total: **672 lines**
  - - `public static AppConfig defaults()` at line 210 — currently calls `builder().build()`, i.e. still uses the Builder
  - - Builder class starts at **line 224**, ends at **line 670** — 447 lines
  - 3. Delete lines 224–670 (the entire Builder class)
