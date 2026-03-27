# Dating App - Development Plan & Project Health

**Date:** 2026-01-10
**Current Status:** Phase 1.5 (Advanced CLI)
**Overall Health:** 8.5/10 (Strong Architecture, Excellent Testing, Minor Refactoring Debt)

---

## 🛑 Current Most Important Problems (List 15)
1.  **Code Duplication in ProfileHandler**: ~200 lines of repetitive logic for editing different fields.
2.  **God Methods in Storage**: `H2UserStorage.save()` exceeds 100 lines; hard to maintain and test.
3.  **Mixed Responsibilities**: `MatchingHandler` contains both UI formatting and swiping logic.
4.  **Inefficient Preference Updates**: `Dealbreakers` record lacks a `mergeFrom` method, forcing manual state copying.
5.  **Exploding Core Package**: 42+ files in `datingapp.core` making navigation difficult.
6.  **Naming Inconsistency**: `DealbreakersEvaluator` and `InterestMatcher` deviate from the `*Service` pattern.
7.  **Missing Service Tests**: `StatsService` currently lacks unit tests despite containing business logic.
8.  **Embedded Serialization**: CSV/List-to-String logic is scattered inside storage implementations rather than centralized.
9.  **Deep Nesting**: UI loops in `MatchingHandler` reach 3-4 levels of indentation.
10. **Magic Numbers in UI**: Scores and offsets are hardcoded in display logic.
11. **Monster Handlers**: `ProfileHandler` (597 lines) and `MatchingHandler` (436 lines) are becoming unmanageable.
12. **Archival Clutter**: Debugging tests like `BugInvestigationTest.java` remain in the main test suite.
13. **Inconsistent String Formatting**: Different handlers use varying box styles and separators.
14. **Main Class Bloat**: `Main.java` handles too much session display logic in its `printMenu`.
15. **User Class Verbosity**: Large number of near-identical setters (each calling `touch()`).

---

## 🛠 Next Changes To Do (List 15)
1.  **Extract `DealbreakersEditor`**: Move editing logic out of `ProfileHandler`.
2.  **Implement `Dealbreakers.Builder.mergeFrom()`**: Simplify partial updates to preferences.
3.  **Create `SerializationUtils`**: Centralize all H2-specific data encoding (Enums to CSV, etc.).
4.  **Extract `MatchDisplayFormatter`**: Move visual rendering logic from `MatchingHandler`.
5.  **Refactor `H2UserStorage`**: Use a dedicated `UserMapper` for ResultSet and Statement binding.
6.  **Standardize Service Naming**: Rename `Evaluator`/`Matcher` to `*Service`.
7.  **Add `StatsServiceTest`**: Achieve full coverage for new stats/achievement features.
8.  **Subpackage `core`**: Group by `domain`, `service`, `storage`, and `config`.
9.  **Add Section Comments**: Mark logical blocks in `User.java` and `ProfileHandler`.
10. **Simplify Menu Logic**: Delegate session status printing to a helper method in `Main`.
11. **Archive Debug Files**: Move investigation tests to a `test/debug` folder or delete them.
12. **Centralize UI Constants**: Move all box-drawing and color strings to `CliConstants`.
13. **Implement Missing Records Tests**: Add `UserAchievementTest` and `PlatformStatsTest`.
14. **Document CandidateFinder**: Clarify why the implementation stays in the `core` layer.
15. **Streamline Setters**: Explore a more concise way to handle `touch()` in `User.java`.

---

## ✨ Next Features to Implement (List 15)
1.  **Super Like**: One high-impact interest signal per day.
2.  **Rewind (Improved Undo)**: Persisted ability to undo the last 5 swipes across sessions.
3.  **Advanced Match Filters**: Pre-filter by education, height, or specific hobbies.
4.  **Profile Prompts**: 3 customizable questions (e.g., "My Sunday is...") on user profiles.
5.  **In-App Messaging System**: Core chat functionality between matches.
6.  **Compatibility Quiz**: Optional questions to refine the `MatchQualityService` score.
7.  **Match Expiration**: Auto-clear matches with no messages after 7 days.
8.  **Conversation Starters**: AI-suggested icebreakers based on shared interests.
9.  **Message Reactions**: React to chat messages with emojis.
10. **Read Receipts**: Visual feedback when a match views a message.
11. **Daily Login Rewards**: Achievement/stat bonuses for consecutive days active.
12. **Anonymous Leaderboards**: See how your "Match Quality" ranks against others.
13. **Incognito Mode**: Hide your profile from everyone EXCEPT people you like.
14. **Travel Mode**: Temporarily swipe in a different location without movingpermanently.
15. **Second Look**: Option to re-review profiles passed over 30 days ago.

---

## 💡 Additional Suggestions / Nice to Have
1.  **ANSI Color Support**: Vibrant UI with colors for high match scores.
2.  **Loading Spinners**: Better feedback for database-heavy operations.
3.  **Profile Completeness Meter**: ASCII progress bar for profile setup.
4.  **Audio Introductions**: Store an optional URL to a voice bio.
5.  **Expanded Photos**: Increase photo limit from 2 to 6.
6.  **Distance Toggle**: Switch between Kilometers and Miles in preferences.
7.  **Bulk Unmatch**: Management screen to clear multiple old matches.
8.  **Export User Data**: Download a JSON summary of your profile and history.
9.  **Dark/Light UI Themes**: ASCII art themes for different moods.
10. **Safety Tips Section**: A dedicated CLI menu for online safety education.
11. **Anonymous Bug Reporter**: Command to log errors directly to a `bugs.log`.
12. **Peak Activity Alerts**: Notify when the app has high concurrent swiping.
13. **Customizable Achievements**: Let "Admin" users define new goals via config.
14. **Match Frequency Settings**: Limit the number of candidates shown per session.
15. **Rich Bio Formatting**: Support simple tags for bold/italic in bios (via CLI).

---

## 📝 Notes
- **Architecture**: The "Core stays pure" rule is being respected, which is excellent.
- **Testing**: 93% class coverage is high, but we must not let it slip with new features.
- **CLI vs UI**: As the CLI reaches its limit, we should consider if partial HTTP/REST is needed soon.
- **Refactoring**: Current debt is centered in the Handlers; focusing here will unlock faster feature development.

## 🚀 Next Steps
1.  **Execute the Refactoring Plan**: Start with `ProfileHandler` and `Dealbreakers`.
2.  **Initialize Messaging**: Lay the groundwork for a `MessageStorage` and `MessageService`.
3.  **Final Polish**: Update `CliConstants` to ensure a premium look and feel across all screens.
