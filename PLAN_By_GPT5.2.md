**Title:** Code-Only Gap Assessment Report (Impact-Ordered + Quick Wins)

**Summary**
- Produce a mid-length, code-backed report that identifies feature, implementation, UI, and QoL gaps across core, storage, CLI, JavaFX UI, and REST API.
- Order findings by user impact/risk, and add a dedicated “Quick Wins” section (low effort / high value).

**Key Changes (Report Content & Structure)**
1. **Impact-Ordered Findings (Top to Bottom)**
   - Super-like is UI-only and behaves as a normal like; no domain model or persistence for super-like.
   - `dailySuperLikeLimit` exists in config but is unused; no enforcement or counters.
   - Daily pass limits exist in config/services but are not enforced in swipe flow.
   - Presence/typing indicators are no-op (UI wired but no backend data source).
   - Pace preferences are required for profile completeness and match quality, but only CLI offers editing; JavaFX uses defaults and offers no UI.
   - Profile verification flow exists in CLI only; no JavaFX UI or REST API endpoint.
   - Config-driven limits for max photos and max interests are not enforced in UI/domain (LocalPhotoStore uses `MAX_PHOTOS=6`, interests use `MAX_PER_USER=10`, independent of config).
   - Soft-delete lifecycle is incomplete: `markDeleted` exists but is unused; `softDeleteRetentionDays` not wired; purge not scheduled.
   - Cleanup routines (sessions, daily picks, standouts, undo) exist but have no scheduler/wiring to run.

2. **Quick Wins Section**
   - Wire pass limit checks into swipe processing.
   - Add config-driven enforcement in UI (max photos/interests) or align constants to config.
   - Add minimal presence/typing adapter (even stubbed test data) or remove indicators to avoid dead UI.

3. **Optional/Strategic Enhancements**
   - Add JavaFX pace-preferences editor (aligns with activation policy).

**Test Plan**
- No tests or code changes in this phase (report only).

**Assumptions**
- Scope is all surfaces (core, storage, CLI, JavaFX UI, REST API).
- Ordering is by impact first, with a dedicated quick wins section.
- Findings are grounded exclusively in current code (documentation ignored as requested).
