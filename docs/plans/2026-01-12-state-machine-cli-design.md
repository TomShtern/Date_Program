# State Machine CLI Redesign

**Date:** 2026-01-12
**Status:** 📋 Design Approved
**Estimated Implementation:** 14-18 hours
**Priority:** High — Unblocks messaging feature testing

---

## Problem Statement

### The Pain Point

The messaging feature cannot be realistically tested in the current CLI because:
- Features exist as **isolated islands** without cohesive navigation
- Switching users requires navigating through menus 9+ times per interaction
- No context preservation — every action dumps you back to the main menu
- 16+ menu options in a flat list with no logical grouping
- No guided user journey connecting: discover → match → message

### Root Cause

The CLI grew organically feature-by-feature, but nobody designed the **connective tissue**. The navigation architecture is fundamentally wrong — it's a flat hub-and-spoke model instead of a contextual flow.

### Current Workflow to Test One Message Exchange

```
1. Main Menu → Option 2 (Select User) → Select Alice
2. Main Menu → Option 4 (Browse) → Like Bob
3. Main Menu → Option 2 → Select Bob
4. Main Menu → Option 4 → Like Alice → Match!
5. Main Menu → Option 2 → Select Alice
6. Main Menu → Option 16 (Conversations) → Select Bob → Type message
7. Main Menu → Option 2 → Select Bob
8. Main Menu → Option 16 → Read message
```

**Result:** 10-15 minutes per test case. Unusable for development.

---

## Solution: State Machine Navigation

### Vision

Replace the flat menu model with a **state machine** that provides:
1. **Guided flows** — The app walks you through natural journeys
2. **Contextual menus** — When viewing a match, options are about that match
3. **Developer mode** — Power-user overlay for fast testing

### Target User Experience

```
Launch → Onboarding (if new) → Profile Setup → Home
                                                 ↓
                                          "Ready to browse!"
                                                 ↓
                                         Browse Candidates
                                           ↓ match!
                                      [Match Context]
                                        → Message them
                                        → View profile
                                        → Unmatch
                                           ↓ message
                                    [Conversation Context]
                                        → Send reply
                                        → View older
                                        → Back to match
```

### Why State Machine (vs Screen-Based)

| Aspect | State Machine | Screen-Based |
|--------|---------------|--------------|
| Navigation model | Current state + defined transitions | Stack of screens (push/pop) |
| Flexibility | Controlled — only valid transitions allowed | Flexible — screens can push anything |
| Visualization | Easy to diagram the whole app | Harder to see all possible paths |
| Invalid states | Prevented by design | Must be checked manually |
| Best for | Apps with known flows (like this) | Apps with dynamic navigation |

**For this dating app, State Machine is better because:**
- The flows are **known** (browse → match → message)
- We want to **prevent** invalid states (can't message without matching)
- It's easier to **visualize** and **test** the whole app as a diagram

---

## Architecture

### States (AppState enum)

```java
public enum AppState {
    // Onboarding
    WELCOME,
    PROFILE_SETUP,

    // Main flow
    HOME,
    BROWSING,
    VIEWING_MATCH,
    IN_CONVERSATION,

    // Other contexts
    VIEWING_PROFILE,
    SETTINGS,
    STATS,
    ACHIEVEMENTS,

    // Safety
    BLOCK_USER,
    REPORT_USER,

    // Developer
    DEVELOPER_MODE,

    // Terminal
    EXIT
}
```

### Context (Shared Navigation State)

```java
public class AppContext {
    // Current user session
    private User currentUser;

    // Navigation context
    private User viewingUser;           // profile being viewed
    private Match currentMatch;         // if in match/conversation context
    private Conversation currentConvo;  // if in conversation

    // Developer mode
    private boolean devModeEnabled;
    private Map<String, User> testUsers; // name → User for quick-switch

    // Services access
    private ServiceRegistry services;

    // Getters, setters, helper methods...

    public void enterDevMode() {
        this.devModeEnabled = true;
        seedTestUsers();
    }

    public void exitDevMode() {
        this.devModeEnabled = false;
    }

    public User resolveUser(String name) {
        return testUsers.get(name.toLowerCase());
    }
}
```

### State Handler Interface

```java
public interface StateHandler {
    /**
     * Get the state this handler manages.
     */
    AppState getState();

    /**
     * Render the screen for this state.
     * Called each time the state is entered or refreshed.
     */
    void render(AppContext context);

    /**
     * Handle user input and return the next state.
     * Return the same state to stay on current screen.
     */
    AppState handleInput(String input, AppContext context);

    /**
     * Optional: Called when entering this state.
     * Use for initialization logic.
     */
    default void onEnter(AppContext context) {}

    /**
     * Optional: Called when leaving this state.
     * Use for cleanup logic.
     */
    default void onExit(AppContext context) {}
}
```

### Main Loop (State Machine Runner)

```java
public class StateMachineApp {
    private final Map<AppState, StateHandler> handlers;
    private final AppContext context;
    private final Scanner scanner;
    private AppState currentState;
    private AppState previousState;

    public StateMachineApp(ServiceRegistry services) {
        this.context = new AppContext(services);
        this.handlers = new HashMap<>();
        this.scanner = new Scanner(System.in);

        registerHandlers();
    }

    private void registerHandlers() {
        register(new WelcomeStateHandler());
        register(new ProfileSetupStateHandler());
        register(new HomeStateHandler());
        register(new BrowsingStateHandler());
        register(new ViewingMatchStateHandler());
        register(new ConversationStateHandler());
        register(new DeveloperModeStateHandler());
        // ... more handlers
    }

    private void register(StateHandler handler) {
        handlers.put(handler.getState(), handler);
    }

    public void run() {
        currentState = AppState.WELCOME;

        while (currentState != AppState.EXIT) {
            StateHandler handler = handlers.get(currentState);

            // Lifecycle: enter
            if (currentState != previousState) {
                handler.onEnter(context);
            }

            // Render current screen
            clearScreen();
            handler.render(context);

            // Get input
            System.out.print("\n> ");
            String input = scanner.nextLine().trim();

            // Developer mode shortcuts (work from any state)
            if (context.isDevModeEnabled()) {
                AppState devResult = handleDevShortcuts(input);
                if (devResult != null) {
                    transition(devResult);
                    continue;
                }
            }

            // Normal input handling
            AppState nextState = handler.handleInput(input, context);
            transition(nextState);
        }

        System.out.println("\nGoodbye!");
    }

    private void transition(AppState nextState) {
        if (nextState != currentState) {
            handlers.get(currentState).onExit(context);
            previousState = currentState;
            currentState = nextState;
        }
    }

    private AppState handleDevShortcuts(String input) {
        // Quick-switch: ~alice, ~bob
        if (input.startsWith("~")) {
            String userName = input.substring(1).trim();
            User user = context.resolveUser(userName);
            if (user != null) {
                context.setCurrentUser(user);
                System.out.println("✓ Switched to " + user.getName());
                return currentState; // stay, but with new user
            }
            System.out.println("❌ User not found: " + userName);
            return null;
        }

        // Slash commands: /match, /msg, /reset
        if (input.startsWith("/")) {
            return handleSlashCommand(input);
        }

        return null; // not a dev shortcut
    }

    private AppState handleSlashCommand(String input) {
        String[] parts = input.substring(1).split("\\s+", 3);
        String command = parts[0].toLowerCase();

        return switch (command) {
            case "match" -> { handleMatchShortcut(parts); yield currentState; }
            case "msg" -> { handleMsgShortcut(parts); yield currentState; }
            case "reset" -> { handleResetShortcut(); yield currentState; }
            case "home" -> AppState.HOME;
            case "dev" -> AppState.DEVELOPER_MODE;
            case "exit" -> AppState.EXIT;
            default -> {
                System.out.println("❌ Unknown command: /" + command);
                yield null;
            }
        };
    }
}
```

---

## State Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                          State Diagram                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   ┌─────────┐      ┌───────────────┐      ┌──────────┐              │
│   │ WELCOME │ ───→ │ PROFILE_SETUP │ ───→ │   HOME   │              │
│   └─────────┘      └───────────────┘      └────┬─────┘              │
│        │                   ↑                   │                     │
│        └───────────────────┴───── (if complete)│                     │
│                                                │                     │
│                    ┌───────────────────────────┼────────────┐        │
│                    │                           │            │        │
│                    ↓                           ↓            ↓        │
│             ┌───────────┐              ┌──────────┐  ┌───────────┐   │
│             │ BROWSING  │              │  STATS   │  │ SETTINGS  │   │
│             └─────┬─────┘              └──────────┘  └───────────┘   │
│                   │                                                  │
│                   │ (match!)                                         │
│                   ↓                                                  │
│          ┌────────────────┐                                          │
│          │ VIEWING_MATCH  │ ←──────────────────────┐                 │
│          └───────┬────────┘                        │                 │
│                  │                                 │                 │
│                  │ (message)                       │ (back)          │
│                  ↓                                 │                 │
│        ┌─────────────────────┐                     │                 │
│        │  IN_CONVERSATION    │ ────────────────────┘                 │
│        └─────────────────────┘                                       │
│                                                                      │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                     DEVELOPER_MODE                           │   │
│   │   Accessible from HOME via menu option [D]                   │   │
│   │   Shortcuts (~user, /cmd) work from ANY state when enabled   │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│   Any state can transition to EXIT (quit)                           │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Developer Mode

### Entry Points

| Method | How |
|--------|-----|
| Menu item | `[D] Developer Mode` from HOME screen |
| Command-line flag | `--dev` launches directly into dev mode |
| Script execution | `--script file.txt` runs commands then exits |

### Capabilities

| Feature | How it works |
|---------|--------------|
| Pre-loaded users | On enable, Alice/Bob/Carol are created and ready |
| Quick-switch | `~alice` from any screen switches current user |
| Shortcuts | `/match alice bob`, `/msg bob "Hi"` from anywhere |
| Reset | `/reset` clears all data and recreates test users |
| Scriptable | `--script test.txt` runs automated scenarios |

### Developer Mode Screen

```
═══════════════════════════════════════════════════
            🛠️  DEVELOPER MODE ACTIVE
═══════════════════════════════════════════════════

Test users: Alice, Bob, Carol (all ACTIVE)
Current user: Alice

Quick Reference:
  ~bob           Switch to Bob
  /match a b     Create mutual like (match)
  /msg bob "Hi"  Send message to Bob
  /reset         Clear all data, recreate test users
  /home          Return to home screen

Commands:
  [1] Show all matches
  [2] Show all conversations
  [3] Show all users
  [4] Create new test user
  [5] Run script file
  [B] Back to HOME (dev mode stays enabled)
  [X] Exit developer mode

>
```

### Slash Commands Reference

| Command | Description | Example |
|---------|-------------|---------|
| `/match <a> <b>` | Create mutual like | `/match alice bob` |
| `/msg <user> "text"` | Send message | `/msg bob "Hello!"` |
| `/like <user>` | Current user likes target | `/like bob` |
| `/pass <user>` | Current user passes | `/pass carol` |
| `/block <user>` | Block user | `/block carol` |
| `/unmatch <user>` | Unmatch | `/unmatch bob` |
| `/reset` | Clear all data | `/reset` |
| `/home` | Go to HOME state | `/home` |
| `/dev` | Toggle developer mode | `/dev` |
| `/exit` | Exit application | `/exit` |

---

## Example State Handlers

### HomeStateHandler

```java
public class HomeStateHandler implements StateHandler {

    @Override
    public AppState getState() {
        return AppState.HOME;
    }

    @Override
    public void render(AppContext ctx) {
        User user = ctx.getCurrentUser();
        int unread = ctx.getServices().getMessagingService()
            .getTotalUnreadCount(user.getId());

        System.out.println("\n═══════════════════════════════════");
        System.out.println("  Welcome back, " + user.getName() + "!");
        System.out.println("═══════════════════════════════════\n");

        System.out.println("  [1] Browse candidates");
        System.out.println("  [2] View matches");
        System.out.print("  [3] Conversations");
        if (unread > 0) {
            System.out.print(" (" + unread + " new)");
        }
        System.out.println();
        System.out.println("  [4] My profile");
        System.out.println("  [5] Stats & achievements");
        System.out.println();
        System.out.println("  [D] Developer mode");
        System.out.println("  [Q] Quit");
    }

    @Override
    public AppState handleInput(String input, AppContext ctx) {
        return switch (input.toLowerCase()) {
            case "1" -> AppState.BROWSING;
            case "2" -> AppState.VIEWING_MATCH;
            case "3" -> AppState.IN_CONVERSATION;
            case "4" -> AppState.VIEWING_PROFILE;
            case "5" -> AppState.STATS;
            case "d" -> AppState.DEVELOPER_MODE;
            case "q" -> AppState.EXIT;
            default -> {
                System.out.println("Invalid option. Try again.");
                yield AppState.HOME;
            }
        };
    }
}
```

### ConversationStateHandler

```java
public class ConversationStateHandler implements StateHandler {
    private static final int PAGE_SIZE = 10;
    private int currentPage = 0;

    @Override
    public AppState getState() {
        return AppState.IN_CONVERSATION;
    }

    @Override
    public void onEnter(AppContext ctx) {
        currentPage = 0;
        // Mark as read when entering
        if (ctx.getCurrentConvo() != null) {
            ctx.getServices().getMessagingService().markAsRead(
                ctx.getCurrentConvo().getId(),
                ctx.getCurrentUser().getId()
            );
        }
    }

    @Override
    public void render(AppContext ctx) {
        Conversation convo = ctx.getCurrentConvo();
        User me = ctx.getCurrentUser();
        User other = ctx.getViewingUser();

        System.out.println("\n═══════════════════════════════════");
        System.out.println("  💬 " + me.getName() + " ↔ " + other.getName());
        System.out.println("═══════════════════════════════════\n");

        // Get messages
        List<Message> messages = ctx.getServices().getMessagingService()
            .getMessages(convo.getId(), PAGE_SIZE, currentPage * PAGE_SIZE);

        if (messages.isEmpty()) {
            System.out.println("  No messages yet. Say hello!\n");
        } else {
            for (Message msg : messages) {
                boolean isMe = msg.senderId().equals(me.getId());
                String sender = isMe ? "You" : other.getName();
                String time = formatTime(msg.createdAt());

                System.out.println("  [" + time + "] " + sender + ":");
                System.out.println("    " + msg.content());
                System.out.println();
            }
        }

        System.out.println("───────────────────────────────────");
        System.out.println("  Type message and press Enter to send");
        System.out.println("  [/older] More messages  [/back] Return to match");
        System.out.println("  [/block] Block user     [/unmatch] Unmatch");
    }

    @Override
    public AppState handleInput(String input, AppContext ctx) {
        if (input.equalsIgnoreCase("/back")) {
            return AppState.VIEWING_MATCH;
        }
        if (input.equalsIgnoreCase("/older")) {
            currentPage++;
            return AppState.IN_CONVERSATION;
        }
        if (input.equalsIgnoreCase("/block")) {
            handleBlock(ctx);
            return AppState.HOME;
        }
        if (input.equalsIgnoreCase("/unmatch")) {
            handleUnmatch(ctx);
            return AppState.HOME;
        }

        // It's a message
        if (!input.isEmpty()) {
            sendMessage(input, ctx);
        }

        return AppState.IN_CONVERSATION; // stay for more messages
    }

    private void sendMessage(String text, AppContext ctx) {
        var result = ctx.getServices().getMessagingService().sendMessage(
            ctx.getCurrentUser().getId(),
            ctx.getViewingUser().getId(),
            text
        );

        if (result.success()) {
            System.out.println("\n  ✓ Message sent\n");
        } else {
            System.out.println("\n  ❌ " + result.errorMessage() + "\n");
        }
    }
}
```

---

## Implementation Plan

### Phase 1: Foundation (3-4 hours)

| Task | Effort | Notes |
|------|--------|-------|
| Create `AppState` enum | 15 min | All states defined |
| Create `AppContext` class | 30 min | With ServiceRegistry access |
| Create `StateHandler` interface | 15 min | With lifecycle hooks |
| Create `StateMachineApp` main loop | 1 hour | Core state machine runner |
| Create `WelcomeStateHandler` | 30 min | Initial state |
| Create `HomeStateHandler` | 45 min | Main hub |
| Wire up and test | 45 min | Basic flow works |

**Milestone:** App launches, shows welcome → home, can quit.

### Phase 2: Critical Path (4-5 hours)

| Task | Effort | Notes |
|------|--------|-------|
| `BrowsingStateHandler` | 1 hour | Wrap existing `MatchingHandler` |
| `ViewingMatchStateHandler` | 1.5 hours | Contextual match screen |
| `ConversationStateHandler` | 1.5 hours | Wrap existing `MessagingHandler` |
| Connect transitions | 30 min | Ensure flow works |
| Test full path | 30 min | Browse → match → message |

**Milestone:** Complete messaging flow in continuous context.

### Phase 3: Developer Mode (2-3 hours)

| Task | Effort | Notes |
|------|--------|-------|
| `DeveloperModeStateHandler` | 1 hour | Main dev mode screen |
| Pre-loaded test users | 30 min | Alice, Bob, Carol on enable |
| Quick-switch (`~user`) | 30 min | Works from any state |
| Slash commands | 45 min | `/match`, `/msg`, `/reset` |
| Test developer workflow | 15 min | Full dev experience |

**Milestone:** Developer mode fully functional.

### Phase 4: Remaining States (3-4 hours)

| Task | Effort | Notes |
|------|--------|-------|
| `ProfileSetupStateHandler` | 45 min | Onboarding flow |
| `ViewingProfileStateHandler` | 45 min | Profile display |
| `StatsStateHandler` | 30 min | Stats and achievements |
| `SettingsStateHandler` | 30 min | App settings |
| Migrate remaining handlers | 1.5 hours | Safety, notes, etc. |

**Milestone:** Full app migrated to state machine.

### Phase 5: Polish (2 hours)

| Task | Effort | Notes |
|------|--------|-------|
| Script execution | 1 hour | `--script file.txt` |
| `--dev` flag | 15 min | Direct dev mode launch |
| Help text | 30 min | Contextual help |
| Final testing | 15 min | End-to-end verification |

**Milestone:** Production-ready state machine CLI.

---

## File Structure

```
src/main/java/datingapp/
├── Main.java                    # Entry point (delegates to StateMachineApp)
├── StateMachineApp.java         # State machine runner
├── AppContext.java              # Shared navigation context
├── AppState.java                # State enum
├── StateHandler.java            # Handler interface
│
├── states/                      # State handlers
│   ├── WelcomeStateHandler.java
│   ├── ProfileSetupStateHandler.java
│   ├── HomeStateHandler.java
│   ├── BrowsingStateHandler.java
│   ├── ViewingMatchStateHandler.java
│   ├── ConversationStateHandler.java
│   ├── ViewingProfileStateHandler.java
│   ├── StatsStateHandler.java
│   ├── SettingsStateHandler.java
│   └── DeveloperModeStateHandler.java
│
├── core/                        # (existing - unchanged)
├── storage/                     # (existing - unchanged)
└── cli/                         # (existing handlers - to be wrapped)
```

---

## Migration Strategy

### Coexistence During Migration

During implementation, both systems can coexist:

```java
public class Main {
    public static void main(String[] args) {
        // New state machine (when ready)
        if (args.length > 0 && args[0].equals("--new")) {
            new StateMachineApp(buildServices()).run();
            return;
        }

        // Existing CLI (default during migration)
        runLegacyCli();
    }
}
```

### Handler Reuse

Existing handlers can be wrapped by state handlers:

```java
public class BrowsingStateHandler implements StateHandler {
    private final MatchingHandler legacyHandler;

    public BrowsingStateHandler(MatchingHandler handler) {
        this.legacyHandler = handler;
    }

    @Override
    public void render(AppContext ctx) {
        // Use legacy handler's display logic
        legacyHandler.showNextCandidate(ctx.getCurrentUser());
    }

    @Override
    public AppState handleInput(String input, AppContext ctx) {
        // Adapt legacy handler's response to state transitions
        // ...
    }
}
```

---

## Success Criteria

### Functional Requirements

- [ ] User can complete browse → match → message in one continuous flow
- [ ] Context is preserved between screens (no "back to main menu" loop)
- [ ] Developer mode accessible from menu
- [ ] Quick-switch works from any screen
- [ ] Slash commands work from any screen
- [ ] Script execution works for automated testing

### Quality Requirements

- [ ] Each state handler is unit testable
- [ ] State transitions are explicit and verifiable
- [ ] No regression in existing functionality
- [ ] Clean separation between states

### Developer Experience

- [ ] Test a messaging scenario in < 30 seconds
- [ ] Pre-loaded users ready on dev mode enable
- [ ] Scriptable test scenarios

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Larger scope than estimated | Delays | Phase 1+2 deliver core value; later phases optional |
| Existing handler coupling | Refactoring difficulty | Wrap don't rewrite; reuse existing logic |
| State explosion | Complexity | Keep states minimal; use context for variations |
| Developer mode bugs | Testing blocked | Implement dev mode early (Phase 3) |

---

## Appendix: Script File Format

```txt
# Test basic messaging flow
# Lines starting with # are comments

# Setup
~alice                          # Switch to Alice
/match alice bob                # Create match

# Send messages
~alice
/msg bob "Hey Bob! How are you?"

~bob
/msg alice "Hi Alice! I'm good!"

# Verify
show conversations

# Cleanup
/reset
```

Run with: `java -jar dating-app.jar --script test-messaging.txt`

---

## Document History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-12 | Initial design from brainstorming session |

---

**Status:** Ready for Implementation ✅
