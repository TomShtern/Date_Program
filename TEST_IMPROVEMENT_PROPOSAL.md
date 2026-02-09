# Test Coverage Improvement Proposal
**Date:** 2026-02-09
**Current Coverage:** ~55-60% (estimated from 119 production files + 48 test files)
**Target Coverage:** 75-80%
**Effort Estimate:** 60-100 hours across 4 tiers

---

## Executive Summary

Your codebase has **excellent core business logic test coverage** (100% of services), but significant gaps in **persistence layer (JDBI/H2)**, **REST API endpoints**, and **ViewModels**. This document prioritizes test improvements with concrete examples and implementation patterns.

### Coverage By Layer

| Layer | Status | Priority | Effort |
|-------|--------|----------|--------|
| Core Services (MatchingService, StatsService, etc.) | ✅ 100% | — | — |
| Storage/JDBI (Database persistence) | ❌ 0% | **TIER 1** | 40-50h |
| REST API Endpoints | ❌ 0% | **TIER 1** | 20-30h |
| ViewModels (JavaFX) | ❌ 11% (1/9) | **TIER 2** | 15-25h |
| CLI Handlers | ⚠️ 62% (8/13) | **TIER 2** | 10-15h |
| UI Controllers | ❌ 0% | **TIER 3** | 30-50h |
| UI Utilities | ❌ 0% | **TIER 3** | 15-25h |

---

## TIER 1: CRITICAL GAPS

### 1. JDBI Storage Layer Integration Tests (0% → 80% coverage)

**Current State:** 21 files with zero test coverage
- JdbiUserStorage, JdbiMatchStorage, JdbiMessagingStorage, etc.
- All database persistence is untested
- Mappers (JdbiUserStorage.Mapper, etc.) never executed in tests

**Why This Matters:**
- Core data model gets corrupted → entire system fails
- EnumSet serialization bugs go undetected
- Complex joins and queries (especially in MatchStorage) are fragile
- Mappers have complex null-handling logic (11+ edge cases per mapper)

**Proposal: H2 Integration Test Suite**

Create a new test file structure:
```
src/test/java/datingapp/storage/
├── JdbiUserStorageTest.java (200 lines)
├── JdbiMatchStorageTest.java (180 lines)
├── JdbiMessagingStorageTest.java (200 lines)
├── JdbiLikeStorageTest.java (120 lines)
├── JdbiBlockStorageTest.java (120 lines)
├── JdbiStatsStorageTest.java (150 lines)
├── JdbiStandoutStorageTest.java (100 lines)
├── DatabaseInitializationTest.java (80 lines) // Schema, migrations, indexes
└── IntegrationTestBase.java (80 lines) // Shared setup
```

**Total: ~1200 lines across 8 tests = 40-50 hours**

#### Example 1: JdbiUserStorageTest
```java
@Timeout(5)
class JdbiUserStorageTest {
    private Jdbi jdbi;
    private JdbiUserStorage userStorage;

    @BeforeEach
    void setUp() {
        // Start H2 in-memory DB
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:test");
        jdbi = Jdbi.create(conn);

        // Initialize schema
        DatabaseManager.initializeSchema(jdbi);

        // Register storage & mappers
        userStorage = jdbi.onDemand(JdbiUserStorage.class);
    }

    @Nested @DisplayName("User CRUD Operations")
    class UserCrud {
        @Test @DisplayName("save() persists all 41 fields")
        void savePersistsAllFields() {
            // Arrange: Create user with complex data
            User user = new User(UUID.randomUUID(), "Alice");
            user.setBirthDate(LocalDate.of(1995, 6, 15));
            user.setGender(Gender.FEMALE);
            user.setInterestedIn(EnumSet.of(Gender.MALE, Gender.NON_BINARY));
            user.setInterests(EnumSet.of(Interest.HIKING, Interest.COOKING));
            user.addPhotoUrl("http://example.com/photo1.jpg");
            user.addPhotoUrl("http://example.com/photo2.jpg");
            user.setDealbreakers(new Dealbreakers(
                EnumSet.of(Lifestyle.Smoking.SMOKER),
                null, null, null, null,
                160, 190, 5
            ));
            user.setPacePreferences(new PacePreferences(
                MessagingFrequency.DAILY,
                TimeToFirstDate.WITHIN_A_WEEK,
                CommunicationStyle.DIRECT,
                DepthPreference.DEEP_CONNECTION
            ));

            // Act
            userStorage.save(UserBindingHelper.from(user));

            // Assert: Retrieve and verify all fields
            User retrieved = userStorage.get(user.getId());
            assertNotNull(retrieved);
            assertEquals(user.getName(), retrieved.getName());
            assertEquals(user.getGender(), retrieved.getGender());
            assertEquals(user.getInterestedIn(), retrieved.getInterestedIn());
            assertEquals(user.getInterests(), retrieved.getInterests());
            assertEquals(2, retrieved.getPhotoUrls().size());
            assertEquals(user.getDealbreakers(), retrieved.getDealbreakers());
            assertEquals(user.getPacePreferences(), retrieved.getPacePreferences());
        }

        @Test @DisplayName("findActive() returns only ACTIVE users")
        void findActiveFiltersState() {
            User alice = TestUserFactory.createActiveUser("Alice");
            User bob = TestUserFactory.createActiveUser("Bob");
            User charlie = TestUserFactory.createCompleteUser("Charlie");
            charlie.setState(UserState.PAUSED);

            userStorage.save(UserBindingHelper.from(alice));
            userStorage.save(UserBindingHelper.from(bob));
            userStorage.save(UserBindingHelper.from(charlie));

            List<User> active = userStorage.findActive();
            assertEquals(2, active.size());
            assertTrue(active.stream().allMatch(u -> u.getState() == UserState.ACTIVE));
        }

        @Test @DisplayName("delete() cascades to profile notes")
        void deleteCascades() {
            User alice = TestUserFactory.createActiveUser("Alice");
            UUID aliceId = alice.getId();

            userStorage.save(UserBindingHelper.from(alice));

            // Add profile note
            ProfileNote note = new ProfileNote(aliceId, UUID.randomUUID(), "Test note");
            userStorage.saveProfileNote(note);

            // Delete user
            userStorage.delete(aliceId);

            // Verify user and note are gone
            assertNull(userStorage.get(aliceId));
            assertTrue(userStorage.getProfileNotesByAuthor(aliceId).isEmpty());
        }
    }

    @Nested @DisplayName("ProfileNote Operations")
    class ProfileNoteOps {
        @Test @DisplayName("getProfileNote() returns exact match")
        void getProfileNoteReturnsExactMatch() {
            UUID author = UUID.randomUUID();
            UUID subject = UUID.randomUUID();

            ProfileNote note = new ProfileNote(author, subject, "Great profile!");
            userStorage.saveProfileNote(note);

            Optional<ProfileNote> retrieved = userStorage.getProfileNote(author, subject);
            assertTrue(retrieved.isPresent());
            assertEquals("Great profile!", retrieved.get().content());
        }
    }
}
```

#### Example 2: JdbiMatchStorageTest
```java
@Timeout(5)
class JdbiMatchStorageTest {
    private Jdbi jdbi;
    private JdbiMatchStorage matchStorage;

    @BeforeEach
    void setUp() {
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:test");
        jdbi = Jdbi.create(conn);
        DatabaseManager.initializeSchema(jdbi);
        matchStorage = jdbi.onDemand(JdbiMatchStorage.class);
    }

    @Nested @DisplayName("Deterministic ID Generation")
    class DeterministicIds {
        @Test @DisplayName("generateId() is order-independent")
        void idOrderIndependent() {
            UUID alice = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            UUID bob = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

            String id1 = Match.generateId(alice, bob);
            String id2 = Match.generateId(bob, alice);

            assertEquals(id1, id2, "ID should be identical regardless of parameter order");
        }

        @Test @DisplayName("save() and get() preserve ID consistency")
        void idConsistencyInDb() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            Match match = new Match(
                Match.generateId(alice, bob),
                alice, bob,
                Match.State.ACTIVE
            );

            matchStorage.save(match);
            Match retrieved = matchStorage.get(match.getId());

            assertEquals(match.getId(), retrieved.getId());
            assertEquals(
                Match.generateId(bob, alice),
                retrieved.getId(),
                "Retrieved ID should equal reverse order ID"
            );
        }
    }

    @Nested @DisplayName("State Transitions")
    class StateTransitions {
        @Test @DisplayName("ACTIVE→FRIENDS transition persists")
        void transitionToFriends() {
            Match match = createActiveMatch();
            matchStorage.save(match);

            match.becomesFriends(match.getUserA());
            matchStorage.save(match);

            Match retrieved = matchStorage.get(match.getId());
            assertEquals(Match.State.FRIENDS, retrieved.getState());
            assertNotNull(retrieved.getEndedAt());
        }

        @Test @DisplayName("ACTIVE→BLOCKED via unmatch persists")
        void blockOnUnmatch() {
            Match match = createActiveMatch();
            matchStorage.save(match);

            match.unmatch(match.getUserB());
            matchStorage.save(match);

            Match retrieved = matchStorage.get(match.getId());
            assertEquals(Match.State.UNMATCHED, retrieved.getState());
            assertEquals(match.getUserB(), retrieved.getEndedBy());
        }
    }
}
```

#### Example 3: IntegrationTestBase
```java
abstract class IntegrationTestBase {
    protected Jdbi jdbi;

    @BeforeEach
    void setUpDb() {
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:test");
        jdbi = Jdbi.create(conn);

        // Apply all schema + indexes
        DatabaseManager.initializeSchema(jdbi);

        // Register all JDBI objects
        jdbi.registerRowMapper(User.class, new JdbiUserStorage.Mapper());
        // ... other mappers
    }

    @AfterEach
    void tearDown() throws SQLException {
        jdbi.withHandle(h -> h.execute("DROP ALL OBJECTS"));
    }

    protected User createActiveTestUser(String name) {
        User u = new User(UUID.randomUUID(), name);
        u.setBirthDate(LocalDate.now().minusYears(25));
        u.setGender(Gender.FEMALE);
        u.setInterestedIn(Set.of(Gender.MALE));
        return u;
    }
}
```

**Testing Strategy:**
- Use H2 in-memory database (`:memory:` mode)
- Each test gets fresh schema
- Test ALL mapper null-handling paths
- Verify MERGE vs INSERT behavior
- Test state transitions and cascading deletes

---

### 2. REST API Endpoint Integration Tests (0% → 85% coverage)

**Current State:**
- `RestApiRoutesTest.java` tests DTOs only
- No actual Javalin server endpoints tested
- No HTTP request/response cycles validated

**Why This Matters:**
- API contract bugs (wrong status codes, missing fields)
- JSON serialization issues
- Route parameter validation failures
- Query parameter handling bugs

**Proposal: Javalin Test Client Suite**

Create new test structure:
```
src/test/java/datingapp/app/api/
├── RestApiUserRoutesTest.java (160 lines)
├── RestApiMatchRoutesTest.java (180 lines)
├── RestApiMessagingRoutesTest.java (140 lines)
├── RestApiErrorHandlingTest.java (100 lines)
└── ApiTestBase.java (80 lines) // Shared setup
```

**Total: ~650 lines across 4 tests = 20-30 hours**

#### Example: RestApiUserRoutesTest
```java
@Timeout(5)
class RestApiUserRoutesTest {
    private TestServer server;
    private TestStorages.Users userStorage;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        CandidateFinder finder = new CandidateFinder(
            userStorage,
            new TestStorages.Likes(),
            new TestStorages.Blocks()
        );

        // Create Javalin app with routes
        Javalin app = Javalin.create();
        UserRoutes routes = new UserRoutes(new TestServiceRegistry(
            userStorage, finder, /* ... */
        ));

        app.get("/api/users", routes::listUsers);
        app.get("/api/users/{id}", routes::getUser);
        app.get("/api/users/{id}/candidates", routes::getCandidates);

        server = new TestServer(app);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Nested @DisplayName("GET /api/users")
    class ListUsersEndpoint {
        @Test @DisplayName("returns 200 with array of users")
        void returnsUserList() {
            User alice = TestUserFactory.createActiveUser("Alice");
            User bob = TestUserFactory.createActiveUser("Bob");
            userStorage.save(alice);
            userStorage.save(bob);

            TestResponse resp = server.get("/api/users");

            assertEquals(200, resp.statusCode());
            List<UserSummary> users = resp.json(new TypeReference<>() {});
            assertEquals(2, users.size());
            assertTrue(users.stream()
                .map(UserSummary::name)
                .allMatch(n -> n.equals("Alice") || n.equals("Bob")));
        }

        @Test @DisplayName("returns empty array when no users")
        void emptyWhenNoUsers() {
            TestResponse resp = server.get("/api/users");

            assertEquals(200, resp.statusCode());
            List<?> users = resp.json(new TypeReference<>() {});
            assertEquals(0, users.size());
        }
    }

    @Nested @DisplayName("GET /api/users/{id}")
    class GetUserEndpoint {
        @Test @DisplayName("returns 200 with user details")
        void returnsUserDetail() {
            User alice = TestUserFactory.createActiveUser("Alice");
            userStorage.save(alice);

            TestResponse resp = server.get("/api/users/" + alice.getId());

            assertEquals(200, resp.statusCode());
            UserDetail detail = resp.json(UserDetail.class);
            assertEquals("Alice", detail.name());
            assertEquals(alice.getAge(), detail.age());
        }

        @Test @DisplayName("returns 404 for non-existent user")
        void returns404ForNotFound() {
            TestResponse resp = server.get("/api/users/" + UUID.randomUUID());
            assertEquals(404, resp.statusCode());
        }

        @Test @DisplayName("returns 400 for invalid UUID")
        void returns400ForInvalidUuid() {
            TestResponse resp = server.get("/api/users/not-a-uuid");
            assertEquals(400, resp.statusCode());
        }
    }

    @Nested @DisplayName("GET /api/users/{id}/candidates")
    class GetCandidatesEndpoint {
        @Test @DisplayName("returns candidates for active user")
        void returnsCandidates() {
            User alice = TestUserFactory.createActiveUser("Alice");
            User candidate1 = TestUserFactory.createActiveUser("Candidate1");
            User candidate2 = TestUserFactory.createActiveUser("Candidate2");

            userStorage.save(alice);
            userStorage.save(candidate1);
            userStorage.save(candidate2);

            TestResponse resp = server.get("/api/users/" + alice.getId() + "/candidates");

            assertEquals(200, resp.statusCode());
            List<UserSummary> candidates = resp.json(new TypeReference<>() {});
            assertEquals(2, candidates.size());
        }
    }
}
```

#### Example: RestApiErrorHandlingTest
```java
@Timeout(5)
class RestApiErrorHandlingTest {
    private TestServer server;

    @BeforeEach
    void setUp() {
        Javalin app = Javalin.create();
        // Register all route handlers
        // ...
        server = new TestServer(app);
    }

    @Test @DisplayName("500 on internal error has error response")
    void internalErrorResponse() {
        // Trigger an exception in a service
        TestResponse resp = server.get("/api/users");

        // Verify response structure
        assertEquals(500, resp.statusCode());
        ErrorResponse error = resp.json(ErrorResponse.class);
        assertNotNull(error.message());
        assertNotNull(error.timestamp());
    }

    @Test @DisplayName("missing path param returns 400")
    void missingPathParamReturns400() {
        TestResponse resp = server.get("/api/users//candidates"); // Missing ID
        assertEquals(400, resp.statusCode());
    }
}
```

---

## TIER 2: HIGH PRIORITY

### 3. ViewModel Unit Tests (11% → 80% coverage)

**Current State:**
- Only `MatchesViewModelTest.java` exists
- 8 ViewModels completely untested
- No async/thread safety tests

**Why This Matters:**
- ViewModels coordinate UI state with business logic
- Race conditions in background threads
- Memory leaks from failed resource cleanup
- Observable properties not updated correctly

**Proposal: ViewModel Unit Test Suite**

Create new test file for each ViewModel:
```
src/test/java/datingapp/ui/viewmodel/
├── LoginViewModelTest.java (140 lines)
├── ChatViewModelTest.java (150 lines)
├── MatchingViewModelTest.java (200 lines) // Expand existing
├── DashboardViewModelTest.java (120 lines)
├── PreferencesViewModelTest.java (100 lines)
├── ProfileViewModelTest.java (100 lines)
├── StatsViewModelTest.java (80 lines)
└── ErrorHandlerTest.java (60 lines)
```

**Total: ~950 lines across 8 tests = 15-25 hours**

#### Example: ChatViewModelTest
```java
@Timeout(5)
class ChatViewModelTest {
    private TestStorages.Messaging messagingStorage;
    private MessagingService messagingService;
    private ChatViewModel viewModel;

    @BeforeEach
    void setUp() {
        messagingStorage = new TestStorages.Messaging();
        messagingService = MessagingService.builder()
            .messagingStorage(messagingStorage)
            .build();

        viewModel = new ChatViewModel(messagingService);
    }

    @Nested @DisplayName("Loading Conversations")
    class LoadingConversations {
        @Test @DisplayName("loading property updates during fetch")
        void loadingPropertyUpdates() {
            AtomicBoolean loadingStarted = new AtomicBoolean(false);
            AtomicBoolean loadingEnded = new AtomicBoolean(false);

            viewModel.loadingProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) loadingStarted.set(true);
                if (!newVal && loadingStarted.get()) loadingEnded.set(true);
            });

            viewModel.loadConversations(UUID.randomUUID());

            assertTrue(loadingStarted.get(), "Loading should be true during fetch");
            assertTrue(loadingEnded.get(), "Loading should be false after fetch");
        }

        @Test @DisplayName("conversations property populated after load")
        void conversationsPopulated() {
            UUID userId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();

            messagingStorage.save(Conversation.create(userId, otherId));
            messagingStorage.save(Conversation.create(userId, UUID.randomUUID()));

            viewModel.loadConversations(userId);

            assertEquals(2, viewModel.conversationsProperty().size());
        }

        @Test @DisplayName("error handler called on exception")
        void errorHandlerCalled() {
            String[] errorMsg = new String[1];
            viewModel.setErrorHandler(msg -> errorMsg[0] = msg);

            // Trigger an error by passing null
            viewModel.loadConversations(null);

            assertNotNull(errorMsg[0], "Error handler should be called");
            assertTrue(errorMsg[0].contains("not null"));
        }
    }

    @Nested @DisplayName("Sending Messages")
    class SendingMessages {
        @Test @DisplayName("sendMessage() adds message to observable")
        void sendMessageUpdatesObservable() {
            String conversationId = "conv_123";
            UUID sender = UUID.randomUUID();

            viewModel.selectConversation(conversationId);
            viewModel.sendMessage(sender, "Hello!");

            List<Message> messages = viewModel.messagesProperty();
            assertEquals(1, messages.size());
            assertEquals("Hello!", messages.get(0).content());
        }
    }
}
```

#### Example: MatchingViewModelTest (Expand Existing)
```java
@Timeout(5)
class MatchingViewModelTest {
    // ... existing tests

    @Nested @DisplayName("Threading & Disposal")
    class ThreadSafety {
        @Test @DisplayName("refreshCandidates() spawns background thread")
        void spawnsBackgroundThread() throws InterruptedException {
            AtomicReference<Thread> bgThread = new AtomicReference<>();
            // Mock to capture thread

            viewModel.refreshCandidates();

            Thread.sleep(100); // Let thread start
            assertNotNull(bgThread.get());
            assertTrue(bgThread.get().isAlive());
        }

        @Test @DisplayName("dispose() interrupts background thread")
        void disposeInterruptsThread() throws InterruptedException {
            viewModel.refreshCandidates();
            Thread.sleep(100);

            viewModel.dispose();

            Thread.sleep(100);
            assertFalse(viewModel.backgroundThread.get().isAlive());
        }

        @Test @DisplayName("currentCandidate property thread-safe updates")
        void threadSafePropertyUpdates() throws InterruptedException {
            List<User> candidates = TestUserFactory.createActiveUsers("User", 5);

            viewModel.setCandidates(candidates);
            viewModel.nextCandidate();
            viewModel.nextCandidate();

            assertNotNull(viewModel.currentCandidateProperty().get());
        }
    }

    @Nested @DisplayName("Match Detection")
    class MatchDetection {
        @Test @DisplayName("lastMatch property updates on mutual like")
        void matchNotification() {
            User alice = TestUserFactory.createActiveUser("Alice");
            User bob = TestUserFactory.createActiveUser("Bob");

            Match[] matchCapture = new Match[1];
            viewModel.lastMatchProperty().addListener((obs, oldVal, newVal) -> {
                matchCapture[0] = newVal;
            });

            viewModel.onMutualLike(alice, bob); // Simulated match

            assertNotNull(matchCapture[0]);
            assertEquals(alice.getId(), matchCapture[0].getUserA());
        }
    }
}
```

---

### 4. CLI Utilities Tests (62% → 90% coverage)

**Current State:**
- 8/13 handlers tested
- Missing: MatchingHandler, ProfileHandler, CliUtilities
- Missing: InputReader abstraction layer

**Proposal: Complete CLI Test Coverage**

Create/expand test files:
```
src/test/java/datingapp/app/cli/
├── MatchingHandlerTest.java (120 lines)
├── ProfileHandlerTest.java (100 lines)
├── CliUtilitiesTest.java (80 lines)
├── EnumMenuTest.java (70 lines)
├── HandlerFactoryTest.java (60 lines)
└── InputReaderTest.java (50 lines)
```

**Total: ~480 lines = 10-15 hours**

#### Example: MatchingHandlerTest
```java
@Timeout(5)
class MatchingHandlerTest {
    private TestStorages.Likes likeStorage;
    private CandidateFinder candidateFinder;
    private MatchingService matchingService;
    private MatchingHandler handler;
    private MockInputReader inputReader;

    @BeforeEach
    void setUp() {
        likeStorage = new TestStorages.Likes();
        candidateFinder = new CandidateFinder(
            new TestStorages.Users(),
            likeStorage,
            new TestStorages.Blocks()
        );
        matchingService = MatchingService.builder()
            .likeStorage(likeStorage)
            .matchStorage(new TestStorages.Matches())
            .build();

        inputReader = new MockInputReader();
        handler = new MatchingHandler(new MatchingHandler.Dependencies(
            candidateFinder, matchingService, /* ... */, AppSession.getInstance()
        ));
    }

    @Nested @DisplayName("Candidate Browsing")
    class CandidateBrowsing {
        @Test @DisplayName("displayCandidate() shows next candidate")
        void displayCandidate() {
            User candidate = TestUserFactory.createActiveUser("Alice");
            // Setup finder to return candidate

            inputReader.setNextInput("1"); // Like

            handler.displayCandidate(candidate);

            // Verify output shows candidate name/bio
            assertTrue(outputCapture.contains("Alice"));
        }

        @Test @DisplayName("handles invalid input gracefully")
        void invalidInputHandling() {
            User candidate = TestUserFactory.createActiveUser("Alice");

            inputReader.setNextInput("invalid");
            inputReader.setNextInput("1"); // Like after invalid

            handler.displayCandidate(candidate);

            // Should re-prompt and accept second input
            assertTrue(likeStorage.exists(userId, candidate.getId()));
        }
    }
}
```

---

## TIER 3: MEDIUM PRIORITY

### 5. UI Controllers & JavaFX Tests (0% → 50% coverage)

**Current State:** 11 UI controllers completely untested

**Challenge:** JavaFX testing requires TestFX framework (headless testing)

**Proposal: Selective JavaFX Integration Tests**

Focus on **critical path** controllers:
- `LoginController` - User login/selection
- `MatchingController` - Swiping workflow
- `ChatController` - Message delivery

Use **TestFX** framework:
```
src/test/java/datingapp/ui/controller/
├── LoginControllerTest.java (150 lines)
├── MatchingControllerTest.java (170 lines)
├── ChatControllerTest.java (140 lines)
└── FxTestBase.java (100 lines)
```

**Total: ~560 lines = 20-30 hours (TestFX has learning curve)**

#### Example: LoginControllerTest
```java
@ExtendWith(FxRobot.class)
class LoginControllerTest {
    private Stage stage;
    private LoginController controller;
    private LoginViewModel viewModel;

    @BeforeAll
    static void setUp() {
        // Initialize JavaFX toolkit
        FxToolkit.registerPrimaryStage();
    }

    @BeforeEach
    void initController() throws Exception {
        FxToolkit.setupStage(stage -> {
            try {
                FXMLLoader loader = new FXMLLoader(
                    getResource("view/Login.fxml")
                );
                Parent root = loader.load();
                controller = loader.getController();
                viewModel = new LoginViewModel(/* mocked services */);
                controller.setViewModel(viewModel);

                stage.setScene(new Scene(root));
                stage.show();
                this.stage = stage;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test @DisplayName("displays user list on initialize")
    void displaysUserList(FxRobot robot) {
        // Wait for list to populate
        robot.waitForFxEvents();

        // Verify users shown
        ListView<?> userList = lookup("#userListView").query();
        assertTrue(userList.getItems().size() > 0);
    }

    @Test @DisplayName("selects user and navigates on double-click")
    void selectUserNavigates(FxRobot robot) {
        // Double-click first user
        robot.clickOn("#userListView")
            .clickOn("#userListView");

        // Verify navigation occurred
        assertEquals(ViewType.DASHBOARD, navigationService.getCurrentView());
    }
}
```

---

## Implementation Roadmap

### Phase 1 (Week 1-2): TIER 1 Critical
**Goal: Get from 55% → 70% coverage**

1. **Day 1-2:** Set up `IntegrationTestBase` + H2 test infrastructure
2. **Day 3-4:** Implement `JdbiUserStorageTest.java` (user CRUD all 41 fields)
3. **Day 5:** Implement `JdbiMatchStorageTest.java` (deterministic IDs, state transitions)
4. **Day 6-7:** Implement `JdbiMessagingStorageTest.java` + other storage tests
5. **Day 8:** REST API test setup + `RestApiUserRoutesTest.java`
6. **Day 9-10:** `RestApiMatchRoutesTest.java` + error handling

**Milestone:** 18 new test files, ~1850 lines, run `mvn verify` → 70% coverage

### Phase 2 (Week 3-4): TIER 2 High Priority
**Goal: Get from 70% → 78% coverage**

1. **Day 1-3:** Expand `MatchingViewModelTest.java` + add 7 new ViewModel tests
2. **Day 4-5:** Complete CLI handler tests

**Milestone:** 8 new test files, ~950 lines, 78% coverage

### Phase 3 (Week 5-6): TIER 3 Medium Priority
**Goal: Get from 78% → 82% coverage (nice-to-have)**

1. **Day 1-5:** TestFX controller tests for critical paths
2. **Day 6:** UI utility tests (Toast, ImageCache, etc.)

**Milestone:** 7 new test files, ~560 lines, 82% coverage

---

## Testing Patterns to Follow

### Pattern 1: In-Memory Storage (for unit tests)
✅ Use `TestStorages.Users`, `TestStorages.Matches`, etc.
✅ No database required
✅ Fast (< 100ms per test)

```java
var userStorage = new TestStorages.Users();
userStorage.save(testUser);
List<User> all = userStorage.findAll();
```

### Pattern 2: Real H2 Database (for storage tests)
✅ Use `Jdbi + H2 in-memory`
✅ Fresh schema per test
✅ Tests JDBI mappers and SQL

```java
Connection conn = DriverManager.getConnection("jdbc:h2:mem:test");
Jdbi jdbi = Jdbi.create(conn);
DatabaseManager.initializeSchema(jdbi);
JdbiUserStorage storage = jdbi.onDemand(JdbiUserStorage.class);
```

### Pattern 3: Javalin Test Client (for API tests)
✅ Use test HTTP client
✅ No mock objects needed
✅ Tests full request/response cycle

```java
TestResponse resp = server.get("/api/users");
assertEquals(200, resp.statusCode());
List<UserSummary> users = resp.json(new TypeReference<>() {});
```

### Pattern 4: ViewModel Observable Testing
✅ Use listener to capture property changes
✅ Use `AtomicReference` to share across lambda
✅ Test error handler integration

```java
String[] errorMsg = new String[1];
viewModel.setErrorHandler(msg -> errorMsg[0] = msg);
viewModel.loadData(null); // Should trigger error
assertNotNull(errorMsg[0]);
```

---

## Success Criteria

✅ **Tier 1 Complete:**
- [ ] All 21 storage files have integration tests
- [ ] All 4 REST API route files have endpoint tests
- [ ] `mvn verify` passes JaCoCo 70% minimum

✅ **Tier 2 Complete:**
- [ ] 8 ViewModels have unit tests
- [ ] CLI utilities fully tested
- [ ] `mvn verify` passes JaCoCo 78% minimum

✅ **Tier 3 Complete:**
- [ ] Critical path controllers tested (LoginController, MatchingController)
- [ ] `mvn verify` passes JaCoCo 82% minimum

---

## Resources & Dependencies

Add to `pom.xml` (if not already present):

```xml
<!-- For API endpoint tests -->
<dependency>
    <groupId>io.javalin</groupId>
    <artifactId>javalin-bundle</artifactId>
    <version>6.4.0</version>
    <scope>test</scope>
</dependency>

<!-- For JavaFX UI tests (TestFX) -->
<dependency>
    <groupId>org.testfx</groupId>
    <artifactId>testfx-core</artifactId>
    <version>4.0.21</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testfx</groupId>
    <artifactId>testfx-junit5</artifactId>
    <version>4.0.21</version>
    <scope>test</scope>
</dependency>

<!-- H2 database already included -->
```

---

## Files to Create

**NEW test files (~26 total, ~3500 LOC):**
```
storage/
  IntegrationTestBase.java
  JdbiUserStorageTest.java
  JdbiMatchStorageTest.java
  JdbiMessagingStorageTest.java
  JdbiLikeStorageTest.java
  JdbiBlockStorageTest.java
  JdbiStatsStorageTest.java
  JdbiStandoutStorageTest.java
  DatabaseInitializationTest.java

app/api/
  ApiTestBase.java
  RestApiUserRoutesTest.java
  RestApiMatchRoutesTest.java
  RestApiMessagingRoutesTest.java
  RestApiErrorHandlingTest.java

ui/viewmodel/
  LoginViewModelTest.java
  ChatViewModelTest.java
  DashboardViewModelTest.java
  PreferencesViewModelTest.java
  ProfileViewModelTest.java
  StatsViewModelTest.java
  ErrorHandlerTest.java

ui/controller/ (TestFX - Phase 3)
  FxTestBase.java
  LoginControllerTest.java
  MatchingControllerTest.java
  ChatControllerTest.java

app/cli/
  MatchingHandlerTest.java
  ProfileHandlerTest.java
  CliUtilitiesTest.java
  EnumMenuTest.java
  HandlerFactoryTest.java
  InputReaderTest.java
```

---

## Quick Start: First Storage Test

Create `/src/test/java/datingapp/storage/IntegrationTestBase.java`:

```java
abstract class IntegrationTestBase {
    protected Jdbi jdbi;
    protected Connection connection;

    @BeforeEach
    protected void initDb() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:test_" + System.nanoTime());
        jdbi = Jdbi.create(connection);
        DatabaseManager.initializeSchema(jdbi);
    }

    @AfterEach
    protected void closeDb() throws SQLException {
        connection.close();
    }
}
```

Then create `/src/test/java/datingapp/storage/JdbiUserStorageTest.java`:

```java
@Timeout(5)
class JdbiUserStorageTest extends IntegrationTestBase {

    private JdbiUserStorage userStorage;

    @BeforeEach
    void setUp() {
        userStorage = jdbi.onDemand(JdbiUserStorage.class);
    }

    @Test @DisplayName("save() and get() round-trip user")
    void roundTripUser() {
        User original = TestUserFactory.createActiveUser("Alice");
        userStorage.save(UserBindingHelper.from(original));

        User retrieved = userStorage.get(original.getId());
        assertEquals(original.getName(), retrieved.getName());
    }
}
```

Run: `mvn test -Dtest=JdbiUserStorageTest`

---

## FAQ

**Q: Why not use Mockito?**
A: Your codebase intentionally avoids Mockito for clarity and simplicity. `TestStorages` in-memory implementations are clearer and maintainable.

**Q: Should I test the UI?**
A: Only critical paths (Phase 3). CLAUDE.md excludes UI from coverage goals. Focus on logic layer first.

**Q: How much time will this take?**
A: Full 82% coverage: 60-100 hours (2-3 sprints). Tier 1 alone (70%): 40-50 hours.

**Q: Which should I prioritize?**
A: **Tier 1** eliminates data corruption bugs. **Tier 2** fixes threading issues. **Tier 3** improves UX reliability.
