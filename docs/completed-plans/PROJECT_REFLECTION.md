# Project Reflection & Roadmap

## 🛑 Major Issues (Current State)
1. **Coupling Overload**: `MatchingHandler` depends on 12+ services, making it a "God Class" hard to test and maintain.
2. **SQL Fragility**: `H2UserStorage.save` uses a massive 30-parameter MERGE statement that breaks easily with schema changes.
3. **UI/Logic Mixing**: CLI Handlers (e.g., `ProfileHandler`) mix console I/O directly with business logic, preventing automated UI testing.
4. **Code Duplication**: `ProfileHandler` contains ~100 lines of repetitive "copyExceptX" builder logic for `Dealbreakers`.
5. **No Auth Security**: "Login" is just selecting a user by ID; no passwords or session tokens exist.
6. **Hardcoded UI Strings**: All user-facing text is embedded in code, making potential localization or tone changes impossible.
7. **Manual Data Parsing**: CSV parsing for lists (e.g., photo URLs, interests) is done manually in Storage, risking data corruption.
8. **Missing Transaction Management**: No clear transaction boundaries; a crash during complex operations could leave data inconsistent.
9. **Test Clutter**: Temporary debugging files (`BugInvestigationTest.java`) act as permanent test suite members.
10. **Magic Numbers**: Scoring weights in `MatchQualityService` and limits in CLI are often hardcoded values.
11. **No Connection Pooling**: `DatabaseManager` appears to use a single connection or simple setup, risking bottlenecks under load.
12. **Verbose User Class**: The `User` entity is becoming a data dump for every new feature (lifestyle, preferences, stats).
13. **Console limitations**: Long output (like candidate lists) scrolls off-screen without pagination.
14. **Lack of Logging Config**: No `logback.xml` or properties, relying on default logging behavior which may hide debug info.
15. **Fragile State Management**: In-memory state in `UserSession` is lost on crash, potentially confusing the user upon restart.

## 🛠 Next Changes (Refactoring & Cleanup)
1. **Extract View Layer**: Create `ConsoleView` class to handle all `System.out` / `InputReader` interaction.
2. **Builder Merge**: Add `mergeFrom(Dealbreakers other)` to `Dealbreakers.Builder` to delete `ProfileHandler` boilerplate.
3. **SQL Mapper**: Extract `UserRowMapper` from `H2UserStorage` to clean up the read logic.
4. **Parameter Object**: Introduce `UserContext` or similar to reduce argument counts in Service methods.
5. **Config Externalization**: Move hardcoded limits (daily likes, distances) to `app.properties`.
6. **Unified Constants**: Move strictly UI strings (prompts, headers) to `CliStrings.java`.
7. **Test Cleanup**: Delete or move `BugInvestigationTest` files to a separate `maintainance` package.
8. **Storage Refactor**: Create `SqlUtils` helper for common tasks like `join/split` of CSV strings.
9. **Handler Slimming**: Split `ProfileHandler` into `ProfileViewer` and `ProfileEditor`.
10. **Pagination**: Implement simple "Page 1 of X" logic for `viewMatches`.
11. **Date Standardization**: specific `DateTimeFormatter` constant used globally.
12. **Defensive Coding**: Add `null` checks in `User` setters to prevent "garbage in".
13. **Javadoc**: Add missing docs to public Service interfaces.
14. **Service Facade**: Create `DatingAppFacade` to reduce the number of services injected into Handlers.
15. **Input Validation**: Centralize input validation logic (age, height ranges) to reuse across handlers.

## 🚀 Next Features (Implementation)
1. **Messaging System**: Simple text-based chat for matched users.
2. **User Passwords**: Add `password_hash` to `User` and real authentication flow.
3. **Photo "Upload"**: Simulate upload by checking if file path exists locally.
4. **Super Like**: Ability to "Super Like" once per day for higher visibility.
5. **Unmatch Feedback**: Ask reason for unmatching (e.g., "Rude", "No chemistry").
6. **Admin Dashboard**: Special login to view platform stats and user reports.
7. **Search Filters**: Allow temporary filters (e.g., "Show only hikers") in `browseCandidates`.
8. **Notification simulation**: "You have 3 new likes" message on login.
9. **Profile Prompts**: "Two truths and a lie" section in profile.
10. **Account Deletion**: Full "Delete My Account" GDPR-compliance feature.
11. **Match Expiry**: Auto-unmatch if no specific interaction happens in 7 days.
12. **Blocked List**: View and manage (unblock) blocked users.
13. **Travel Mode**: Set location manually to browse other cities.
14. **Age Verification**: Simple math question or date check to "verify" age.
15. **User Sentiment**: "Rate this app" prompt after 5 matches.

## 💡 Suggestions (Nice to Have)
1. **Jansi Colors**: Add color to the console (Green for Match, Red for Block).
2. **Mock Data Generator**: Script to generate 50 realistic users for testing.
3. **Backup Command**: CLI command to export the H2 database to a backup file.
4. **Interactive Shell**: Use JLine for arrow-key navigation in menus.
5. **Progress Bars**: Animated ASCII loading bars for "Finding candidates...".
6. **Docker Support**: `Dockerfile` to run the app in a container.
7. **CI Pipeline**: GitHub Actions workflow for build and test.
8. **Sound Effects**: Terminal bell (`\a`) on Match.
9. **Easter Eggs**: Secret commands (e.g., `konami` code).
10. **Tips System**: "Did you know?" one-liners on startup.
11. **Performance Stats**: Debug command to show query execution times.
12. **Match export**: Save match details to a text file.
13. **Timezone Support**: Better handling of user timezones.
14. **Undo History**: Persist undo capability across restarts.
15. **ASCII Logo**: A generated ASCII art banner based on user name.

## 📝 Notes
*   **Architecture**: "Core stays pure" is well-respected, but the CLI layer is becoming a "monolith of handlers".
*   **Data Integrity**: H2 is fine, but raw SQL queries are becoming unmanageable. Consider a lightweight wrapper like JDBI or just better SQL abstractions if JPA is forbidden.
*   **Engagement**: The app has good "bones" (matching, stats), but lacks the "glue" (messaging/interaction) that makes a dating app distinct from a directory.
*   **Testing**: Test coverage is high effectively, but the "glue code" in CLI is completely untested.

## ⏩ Next Steps
1.  **Refactor**: Attack the `MatchingHandler` coupling and `Dealbreakers` duplication first.
2.  **Auth**: Implement basic password security.
3.  **Messaging**: Build the `MessageService` to enable actual user interaction.
