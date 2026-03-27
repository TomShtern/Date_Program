# PART 5: TEST COVERAGE GAPS

## Critical Coverage Gaps (P0)

| Class/Method                              | Coverage | Impact                               |
|-------------------------------------------|----------|--------------------------------------|
| `CandidateFinder.GeoUtils.distanceKm()`   | 0%       | Distance calculation bugs undetected |
| `User.ProfileNote` (entire nested record) | 0%       | Validation logic untested            |
| `User.DatabaseRecord.Builder`             | 0%       | 20+ field builder untested           |
| `User.ProfileNoteStorage`                 | 0%       | Storage interface untested           |
| `User.ProfileViewStorage`                 | 0%       | Storage interface untested           |

## Missing Edge Case Tests

| Class                  | Method                 | Missing Test             |
|------------------------|------------------------|--------------------------|
| User                   | setAgeRange(18, 18)    | Same min/max boundary    |
| User                   | addInterest(duplicate) | Idempotence test         |
| Match.generateId()     | Same UUIDs             | Self-match prevention    |
| SwipeSession           | recordSwipe()          | Negative duration        |
| Dealbreakers.Evaluator | All                    | Null user fields         |
| Message                | Constructor            | Exactly MAX_LENGTH chars |

## Missing State Transition Tests

- Match: FRIENDS→UNMATCHED, FRIENDS→BLOCKED, invalid paths
- User: PAUSED→INCOMPLETE (should fail?)
- SwipeSession: incrementMatchCount on COMPLETED session

## Missing Concurrency Tests

1. Match creation race condition
2. Conversation creation by both users simultaneously
3. Like recording twice concurrently
4. User state transitions (concurrent activation/ban)

## Missing Integration Tests

- H2ProfileDataStorage
- H2SocialStorage
- H2MetricsStorage
- H2ModerationStorage (partial)
- Service-to-storage round-trips

---