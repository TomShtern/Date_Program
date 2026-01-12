# Testing REPL Design Document

**Date:** 2026-01-11
**Purpose:** Enable fast multi-user testing in console-only environment
**Status:** 📋 Design Phase
**Estimated Implementation:** 6-8 hours

---

## Problem Statement

The messaging feature cannot be realistically tested in the current CLI because:
- Switching users requires navigating through menus 9+ times per interaction
- No way to simulate concurrent users in a single terminal
- Manual testing takes 10-15 minutes per test case
- Feels like "checking voicemail" instead of live chat

**Current workflow to send one message:**
1. Main Menu → Option 2 (Select User) → Select Alice
2. Main Menu → Option 4 (Browse) → Like Bob
3. Main Menu → Option 2 → Select Bob
4. Main Menu → Option 4 → Like Alice → Match!
5. Main Menu → Option 2 → Select Alice
6. Main Menu → Option 16 (Conversations) → Select Bob → Type message
7. Main Menu → Option 2 → Select Bob
8. Main Menu → Option 16 → Read message

**Problem:** This is unusable for developer testing.

---

## Solution: Testing REPL

Build a **command-line interface for orchestrating multi-user scenarios** - a REPL (Read-Eval-Print Loop) specifically for testing.

### What It Is
- Separate console mode launched with `--test` flag
- Command-based interface instead of menu-based
- Direct service calls without navigation
- Pre-loaded test users ready to interact

### What It's NOT
- NOT a replacement for the normal CLI (that's for user experience)
- NOT a replacement for integration tests (that's for CI/CD)
- NOT for real users (developer tool only)

### Key Insight
> **The normal CLI is for USER EXPERIENCE. The testing REPL is for DEVELOPER EXPERIENCE.**
> These can be different interfaces over the same core services.

---

## Design

### 1. Launch Mode

```bash
# Normal CLI (existing)
mvn exec:java

# Testing REPL (new)
mvn exec:java -Dexec.args="--test"

# Or with JAR
java -jar dating-app.jar --test
```

### 2. Interface Example

```
Dating App - Testing Console
========================================
Pre-loaded users: Alice, Bob, Carol (all ACTIVE with complete profiles)
Type 'help' for commands, 'exit' to quit

dating-app> alice like bob
✓ Alice liked Bob

dating-app> bob like alice
✓ Bob liked Alice
🎉 Match created! Alice ↔ Bob

dating-app> alice msg bob "Hey Bob! How are you?"
✓ Message sent (Bob now has 1 unread)

dating-app> bob read
💬 Conversations (1)
  Alice (1 new) · just now
    "Hey Bob! How are you?"

dating-app> bob read alice
═══════════════════════════════════
       💬 Conversation: Bob ↔ Alice
═══════════════════════════════════
   [Jan 11, 2:30 PM] Alice:
   Hey Bob! How are you?

dating-app> bob msg alice "Hi Alice! I'm good, thanks!"
✓ Message sent (Alice now has 1 unread)

dating-app> show matches
Alice ↔ Bob (ACTIVE, 2 messages)

dating-app> alice unread
Alice has 1 unread message

dating-app> reset
⚠️  This will delete ALL data. Are you sure? (yes/no): yes
✓ All data cleared. Test users recreated.

dating-app> exit
Goodbye!
```

---

## Command Reference

### User Actions

| Command | Description | Example |
|---------|-------------|---------|
| `<user> like <user>` | User likes another user | `alice like bob` |
| `<user> pass <user>` | User passes on another user | `alice pass carol` |
| `<user> msg <user> "text"` | Send message | `bob msg alice "Hi!"` |
| `<user> read` | View all conversations | `alice read` |
| `<user> read <user>` | View specific conversation | `alice read bob` |
| `<user> block <user>` | Block user | `alice block bob` |
| `<user> unmatch <user>` | Unmatch | `alice unmatch bob` |

### Shortcuts

| Command | Description | Example |
|---------|-------------|---------|
| `match <user> <user>` | Auto-create mutual like (match) | `match alice bob` |
| `convo <user> <user> "msg"` | Match + send message in one step | `convo alice bob "Hi!"` |

### Viewing State

| Command | Description | Example |
|---------|-------------|---------|
| `show matches` | List all matches | - |
| `show conversations` | List all conversations | - |
| `show users` | List all users | - |
| `<user> unread` | Show unread count | `alice unread` |
| `<user> stats` | Show user statistics | `bob stats` |
| `<user> profile` | View user profile | `carol profile` |

### Data Management

| Command | Description | Example |
|---------|-------------|---------|
| `user create <name>` | Create new test user | `user create Dave` |
| `user delete <name>` | Delete user | `user delete Dave` |
| `reset` | Clear all data (with confirmation) | `reset` |
| `seed` | Recreate default test users | `seed` |

### Utilities

| Command | Description | Example |
|---------|-------------|---------|
| `help` | Show command list | - |
| `help <command>` | Show command details | `help msg` |
| `exit` | Quit testing console | - |
| `clear` | Clear screen | - |

---

## Implementation Plan

### Phase 1: Core REPL (3 hours)

#### File: `TestingConsole.java`

```java
package datingapp;

public class TestingConsole {
    private ServiceRegistry services;
    private Scanner scanner;
    private Map<String, User> testUsers; // name → User mapping

    public static void main(String[] args) {
        TestingConsole console = new TestingConsole();
        console.run();
    }

    private void run() {
        initialize();
        printWelcome();
        seedTestUsers();
        replLoop();
        shutdown();
    }

    private void replLoop() {
        while (true) {
            System.out.print("dating-app> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;
            if (input.equalsIgnoreCase("exit")) break;

            try {
                executeCommand(input);
            } catch (Exception e) {
                System.out.println("❌ Error: " + e.getMessage());
            }
        }
    }

    private void executeCommand(String input) {
        String[] parts = input.split("\\s+", 3);
        String command = parts[0].toLowerCase();

        switch (command) {
            case "help" -> showHelp();
            case "show" -> handleShow(parts);
            case "match" -> handleMatch(parts);
            case "reset" -> handleReset();
            case "user" -> handleUserManagement(parts);
            default -> handleUserAction(parts);
        }
    }

    private void handleUserAction(String[] parts) {
        // Parse: <user> <action> [target] [args]
        String userName = parts[0];
        String action = parts[1].toLowerCase();

        User user = testUsers.get(userName);
        if (user == null) {
            System.out.println("❌ User not found: " + userName);
            return;
        }

        switch (action) {
            case "like" -> handleLike(user, parts[2]);
            case "msg" -> handleMessage(user, parts);
            case "read" -> handleRead(user, parts);
            case "block" -> handleBlock(user, parts[2]);
            // ... more actions
        }
    }
}
```

#### File: `Main.java` (modify)

```java
public static void main(String[] args) {
    // Check for --test flag
    if (args.length > 0 && args[0].equals("--test")) {
        TestingConsole.main(args);
        return;
    }

    // Existing normal CLI code
    try (Scanner scanner = new Scanner(System.in)) {
        initializeApp(scanner);
        // ... rest of existing code
    }
}
```

### Phase 2: Command Handlers (2 hours)

Implement each command handler:

**Like Command:**
```java
private void handleLike(User user, String targetName) {
    User target = testUsers.get(targetName);
    if (target == null) {
        System.out.println("❌ User not found: " + targetName);
        return;
    }

    Like like = Like.create(user.getId(), target.getId(), Like.Direction.LIKE);
    services.getLikeStorage().save(like);

    System.out.println("✓ " + user.getName() + " liked " + target.getName());

    // Check for match
    Optional<Match> match = services.getMatchingService().recordLike(like);
    if (match.isPresent()) {
        System.out.println("🎉 Match created! " + user.getName() + " ↔ " + target.getName());
    }
}
```

**Message Command:**
```java
private void handleMessage(User user, String[] parts) {
    // Parse: <user> msg <target> "message text"
    String targetName = parts[2];
    String message = extractQuotedText(parts);

    User target = testUsers.get(targetName);
    if (target == null) {
        System.out.println("❌ User not found: " + targetName);
        return;
    }

    MessagingService.SendResult result =
        services.getMessagingService().sendMessage(
            user.getId(), target.getId(), message);

    if (result.success()) {
        int unreadCount = services.getMessagingService()
            .getUnreadCount(target.getId(),
                Conversation.generateId(user.getId(), target.getId()));
        System.out.println("✓ Message sent (" + target.getName() +
            " now has " + unreadCount + " unread)");
    } else {
        System.out.println("❌ " + result.errorMessage());
    }
}
```

**Read Command:**
```java
private void handleRead(User user, String[] parts) {
    if (parts.length == 2) {
        // Show all conversations
        showConversationList(user);
    } else {
        // Show specific conversation
        String targetName = parts[2];
        User target = testUsers.get(targetName);
        if (target != null) {
            showConversation(user, target);
        }
    }
}

private void showConversation(User user, User other) {
    List<Message> messages = services.getMessagingService()
        .getMessages(user.getId(), other.getId(), 50, 0);

    System.out.println("═══════════════════════════════════");
    System.out.println("       💬 Conversation: " + user.getName() +
        " ↔ " + other.getName());
    System.out.println("═══════════════════════════════════");

    for (Message msg : messages) {
        boolean isFromMe = msg.senderId().equals(user.getId());
        String sender = isFromMe ? "You" : other.getName();
        String timestamp = formatTimestamp(msg.createdAt());

        System.out.println("   [" + timestamp + "] " + sender + ":");
        System.out.println("   " + msg.content());
        System.out.println();
    }
}
```

### Phase 3: Test User Setup (1 hour)

```java
private void seedTestUsers() {
    System.out.println("\nCreating test users...");

    testUsers.put("alice", createTestUser("Alice"));
    testUsers.put("bob", createTestUser("Bob"));
    testUsers.put("carol", createTestUser("Carol"));

    System.out.println("✓ Test users ready: Alice, Bob, Carol");
    System.out.println();
}

private User createTestUser(String name) {
    User user = User.fromDatabase(
        UUID.randomUUID(),
        name,
        "Test bio for " + name,
        LocalDate.of(1990, 1, 1),
        User.Gender.OTHER,
        EnumSet.allOf(User.Gender.class),
        32.0 + Math.random(), // Random near Tel Aviv
        34.0 + Math.random(),
        100,
        20,
        50,
        List.of("photo.jpg"),
        User.State.ACTIVE,
        Instant.now(),
        Instant.now(),
        EnumSet.noneOf(Interest.class),
        null, null, null, null, null, null, null
    );

    services.getUserStorage().save(user);
    return user;
}
```

### Phase 4: Shortcuts & Helpers (1 hour)

```java
private void handleMatch(String[] parts) {
    // Shortcut: match <userA> <userB>
    // Auto-creates mutual like
    String nameA = parts[1];
    String nameB = parts[2];

    User userA = testUsers.get(nameA);
    User userB = testUsers.get(nameB);

    if (userA == null || userB == null) {
        System.out.println("❌ User not found");
        return;
    }

    // Create both likes
    Like likeAtoB = Like.create(userA.getId(), userB.getId(), Like.Direction.LIKE);
    Like likeBtoA = Like.create(userB.getId(), userA.getId(), Like.Direction.LIKE);

    services.getMatchingService().recordLike(likeAtoB);
    Optional<Match> match = services.getMatchingService().recordLike(likeBtoA);

    if (match.isPresent()) {
        System.out.println("🎉 Match created! " + nameA + " ↔ " + nameB);
    } else {
        System.out.println("⚠️  Match creation failed");
    }
}

private void handleShow(String[] parts) {
    String what = parts[1].toLowerCase();

    switch (what) {
        case "matches" -> showAllMatches();
        case "conversations" -> showAllConversations();
        case "users" -> showAllUsers();
        default -> System.out.println("❌ Unknown: show " + what);
    }
}

private void showAllMatches() {
    System.out.println("\n📊 All Matches:");
    for (User user : testUsers.values()) {
        List<Match> matches = services.getMatchStorage()
            .getActiveMatchesFor(user.getId());

        for (Match match : matches) {
            UUID otherId = match.getOtherUser(user.getId());
            User other = services.getUserStorage().get(otherId);
            if (other != null) {
                int msgCount = services.getMessagingService()
                    .getMessages(user.getId(), other.getId(), 1000, 0).size();
                System.out.println("  " + user.getName() + " ↔ " +
                    other.getName() + " (" + msgCount + " messages)");
            }
        }
    }
    System.out.println();
}
```

### Phase 5: Advanced Features (Optional, 2 hours)

**Script Support:**
```java
// Launch with script file
if (args.length > 1 && args[0].equals("--test") && args[1].equals("--script")) {
    runScript(args[2]);
    return;
}

private void runScript(String filename) {
    try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
        String line;
        int lineNum = 1;

        while ((line = reader.readLine()) != null) {
            line = line.trim();

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) {
                lineNum++;
                continue;
            }

            System.out.println("\n[" + lineNum + "] " + line);
            executeCommand(line);

            lineNum++;
        }

        System.out.println("\n✓ Script completed");
    } catch (IOException e) {
        System.err.println("❌ Script error: " + e.getMessage());
    }
}
```

**Example Script File (`test-scenario.txt`):**
```
# Basic conversation test
match alice bob
alice msg bob "Hey Bob! How are you?"
bob read
bob msg alice "Hi Alice! I'm good, thanks!"
alice read bob
show conversations
```

---

## Benefits

### ✅ Solves Core Problem
- Reduces 9-step menu navigation to 1 command
- Test messaging in seconds instead of minutes
- Natural syntax matching how developers think

### ✅ Fast Developer Workflow
```
# Old way: 2 minutes per interaction
1. Navigate to select user
2. Navigate to browse
3. Scroll to find user
4. Like user
5. Navigate back
6. Switch user
7. Repeat...

# New way: 5 seconds
dating-app> match alice bob
dating-app> alice msg bob "Hi!"
dating-app> bob read
```

### ✅ Scriptable Testing
```bash
# Run automated test scenarios
java -jar app.jar --test --script basic-conversation.txt
java -jar app.jar --test --script edge-cases.txt
```

### ✅ Complements Integration Tests
- Integration tests (JUnit) → Automated CI/CD verification
- Testing REPL → Interactive manual exploration
- Both use same services, different interfaces

### ✅ Console-Only
- No GUI framework needed
- No web server needed
- Pure Java standard input/output
- Works in any terminal

### ✅ Realistic Multi-User Testing
- Can interleave actions between users naturally
- Test concurrent scenarios
- See immediate feedback
- Verify unread counts, match states, etc.

---

## Limitations & Trade-offs

### What It Doesn't Do
- **Not real-time:** Still command-driven, not event-driven
- **Not for end users:** Developer tool only
- **Not a replacement for automated tests:** Manual exploration tool
- **Single terminal:** Can't run two REPLs simultaneously (but don't need to)

### What It Does Do
- Makes messaging testable by developers
- Enables rapid iteration during development
- Provides scriptable test scenarios
- Console-only as required

---

## Estimated Implementation

| Phase | Description | Time |
|-------|-------------|------|
| 1 | Core REPL loop | 3 hours |
| 2 | Command handlers | 2 hours |
| 3 | Test user setup | 1 hour |
| 4 | Shortcuts & helpers | 1 hour |
| 5 | Script support (optional) | 2 hours |
| **Total** | **Basic: 7 hours, Full: 9 hours** | |

---

## Testing the Testing REPL

### Manual Verification

1. Launch: `mvn exec:java -Dexec.args="--test"`
2. Verify test users exist: `show users`
3. Create match: `match alice bob`
4. Send message: `alice msg bob "Hello!"`
5. Check unread: `bob unread` → Should show 1
6. Read: `bob read alice` → Should show message
7. Reply: `bob msg alice "Hi!"`
8. Check unread: `alice unread` → Should show 1
9. Verify: `show matches` → Should show Alice ↔ Bob with 2 messages
10. Clean up: `reset` → Should clear data

### Automated Script Test

Create `test-basic-flow.txt`:
```
# Test basic messaging flow
show users
match alice bob
alice msg bob "Test message 1"
bob read
bob msg alice "Test reply 1"
alice read bob
show conversations
```

Run: `mvn exec:java -Dexec.args="--test --script test-basic-flow.txt"`

Verify: All commands execute without errors.

---

## Future Enhancements

### Phase 2 Features (if needed)
1. **Assertions:** `assert alice unread == 1` for test verification
2. **Sleep:** `sleep 5` for time-based testing
3. **Inspect:** `inspect match alice-bob` for detailed object view
4. **Watch:** `watch alice unread` for monitoring changes
5. **Undo:** `undo` to reverse last action
6. **Variables:** `$match = match alice bob` for reuse
7. **Conditionals:** `if alice unread > 0 then alice read` for logic

### Integration with CI/CD
```bash
# Run test scenarios in CI pipeline
./run-test-scenarios.sh
# Exits with code 0 if all pass, 1 if any fail
```

---

## Conclusion

The Testing REPL provides a **fast, natural, console-only interface** for multi-user testing. It reduces a 9-step menu navigation to a single command, making the messaging feature actually testable by developers without extreme manual effort.

**Key Takeaway:**
> By separating USER EXPERIENCE (normal CLI) from DEVELOPER EXPERIENCE (testing REPL), we get the best of both worlds: a polished user interface AND an efficient testing tool.

**Recommendation:** Implement Phase 1-4 (7 hours) to immediately unblock messaging development and testing.

---

**Document Version:** 1.0
**Last Updated:** 2026-01-11
**Status:** Ready for Implementation ✅
