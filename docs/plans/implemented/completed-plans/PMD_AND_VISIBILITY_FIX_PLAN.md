# PMD Violations & IDE Visibility Fix Plan

**Created:** 2026-01-29
**Status:** Ready for execution
**Scope:** Fix 254 PMD violations + 3 IDE compilation errors
**Estimated changes:** ~100 files affected

---

## Executive Summary

This plan addresses two categories of issues:

1. **IDE Compilation Errors (3 issues)** - Nested types not visible outside `core/` package
2. **PMD Violations (254 issues)** - Advisory code quality warnings

**Priority Order:** Fix IDE compilation errors FIRST (blocking), then PMD violations (non-blocking).

---

## Part 1: IDE Compilation Errors (NOT BLOCKING - STALE)

### Status: ✅ RESOLVED - No action needed

**Investigation Results (2026-01-29):**
After running `mvn compile`, no compilation errors occurred. The IDE errors are **stale cache issues**.

| Type | Location | Actual Status |
|------|----------|---------------|
| `Interest.Category` | `Preferences.java:84` | `public enum Category` ✅ |
| `User.State` | `User.java:30` | `public enum State` ✅ |
| `UserInteractions.Block` | `UserInteractions.java:60` | `public record Block` ✅ |

### Resolution
The visibility chain is correct:
- `Preferences` → `public final class` ✅
- `Interest` → `public enum` ✅
- `Category` → `public enum` ✅

**IDE Fix:** Restart the Java Language Server or run "Java: Clean Java Language Server Workspace" in VS Code.

### Verification
```bash
mvn compile  # Succeeds with no errors
```

---

## Part 2: PMD Violations (ADVISORY - 254 total)

### Violation Breakdown by Category

| Rule | Count | Effort | Priority |
|------|-------|--------|----------|
| GuardLogStatement | ~150 | Medium | 3 (Skip - see rationale) |
| LiteralsFirstInComparisons | ~25 | Easy | 1 |
| DanglingJavadoc | ~8 | Easy | 1 |
| MissingStaticMethodInNonInstantiatableClass | ~8 | Medium | 2 |
| LambdaCanBeMethodReference | ~7 | Easy | 1 |
| UselessParentheses | ~7 | Easy | 1 |
| LooseCoupling | ~6 | Skip | - (JDBI requirement) |
| UncommentedEmptyMethodBody | ~3 | Easy | 1 |
| UnnecessaryFullyQualifiedName | ~3 | Easy | 1 |
| UnnecessaryModifier | ~3 | Easy | 1 |
| UseUtilityClass | ~2 | Easy | 1 |
| ClassWithOnlyPrivateConstructorsShouldBeFinal | ~2 | Easy | 1 |
| SimplifyBooleanReturns | ~2 | Easy | 1 |
| EmptyCatchBlock | ~2 | Easy | 1 |
| SingularField | ~2 | Medium | 2 |
| AvoidBranchingStatementAsLastInLoop | ~2 | Easy | 1 |
| AvoidUsingVolatile | ~2 | Skip | - (Intentional for thread safety) |
| UseLocaleWithCaseConversions | ~2 | Easy | 1 |
| UnusedPrivateField | ~1 | Easy | 1 |
| UnnecessaryConstructor | ~1 | Easy | 1 |
| MissingOverride | ~1 | Easy | 1 |
| CloseResource | ~1 | Medium | 2 |

### Fix Strategy by Priority

---

### Priority 1: Quick Wins (~60 fixes)

#### 2.1 LiteralsFirstInComparisons (~25 fixes)
**Rule:** Put string/number literals on the left side of `.equals()` to prevent NPE.

**Pattern:**
```java
// BEFORE
if (input.equals("yes")) { ... }
if (status.equals(Status.ACTIVE)) { ... }

// AFTER
if ("yes".equals(input)) { ... }
if (Status.ACTIVE.equals(status)) { ... }
```

**Search command:**
```bash
rg -n '\.equals\(' src/main/java --type java
```

**Files likely affected:** CLI handlers, services with string comparisons

---

#### 2.2 LambdaCanBeMethodReference (~7 fixes)
**Rule:** Replace lambda with method reference when possible.

**Pattern:**
```java
// BEFORE
list.forEach(item -> System.out.println(item));
list.stream().map(s -> s.toLowerCase());
Optional.map(u -> u.getName());

// AFTER
list.forEach(System.out::println);
list.stream().map(String::toLowerCase);
Optional.map(User::getName);
```

**Search command:**
```bash
rg -n '\->' src/main/java --type java -A1
```

---

#### 2.3 UselessParentheses (~7 fixes)
**Rule:** Remove unnecessary parentheses that don't affect precedence.

**Pattern:**
```java
// BEFORE
return (value);
int x = (a + b);
if ((condition)) { ... }

// AFTER
return value;
int x = a + b;
if (condition) { ... }
```

---

#### 2.4 DanglingJavadoc (~8 fixes)
**Rule:** Javadoc comment not attached to any code element.

**Pattern:**
```java
// BEFORE - orphaned javadoc
/** This method does X */

public void doSomething() { ... }  // gap causes "dangling"

// AFTER - attach directly
/** This method does X */
public void doSomething() { ... }
```

**Or delete if the javadoc describes removed code.**

---

#### 2.5 UnnecessaryFullyQualifiedName (~3 fixes)
**Rule:** Use imports instead of fully qualified names.

**Pattern:**
```java
// BEFORE
java.util.List<String> items = new java.util.ArrayList<>();

// AFTER (with import)
import java.util.List;
import java.util.ArrayList;
List<String> items = new ArrayList<>();
```

---

#### 2.6 UnnecessaryModifier (~3 fixes)
**Rule:** Remove redundant modifiers (e.g., `public` on interface methods).

**Pattern:**
```java
// BEFORE - interface methods are implicitly public
public interface Storage {
    public void save(User user);  // redundant 'public'
}

// AFTER
public interface Storage {
    void save(User user);
}
```

---

#### 2.7 UncommentedEmptyMethodBody (~3 fixes)
**Rule:** Empty method bodies should have a comment explaining why.

**Pattern:**
```java
// BEFORE
@Override
public void onEvent(Event e) {
}

// AFTER
@Override
public void onEvent(Event e) {
    // No-op: event handling not required for this implementation
}
```

---

#### 2.8 EmptyCatchBlock (~2 fixes)
**Rule:** Don't silently swallow exceptions.

**Pattern:**
```java
// BEFORE
try {
    riskyOperation();
} catch (Exception e) {
}

// AFTER - at minimum, log or comment
try {
    riskyOperation();
} catch (Exception e) {
    // Intentionally ignored: non-critical operation
}
// Or better:
catch (Exception e) {
    logger.debug("Non-critical failure", e);
}
```

---

#### 2.9 UseUtilityClass (~2 fixes)
**Rule:** Classes with only static methods should have private constructor.

**Pattern:**
```java
// BEFORE
public class StringUtils {
    public static String trim(String s) { ... }
}

// AFTER
public final class StringUtils {
    private StringUtils() {} // Prevent instantiation
    public static String trim(String s) { ... }
}
```

**Note:** `Main.java` may trigger this - evaluate if appropriate.

---

#### 2.10 ClassWithOnlyPrivateConstructorsShouldBeFinal (~2 fixes)
**Rule:** If only private constructors exist, class should be `final`.

Already fixed some in previous session. Check remaining:
```bash
rg -l 'private.*\(\)' src/main/java --type java | xargs rg 'public class'
```

---

#### 2.11 SimplifyBooleanReturns (~2 fixes)
**Rule:** Simplify boolean return statements.

**Pattern:**
```java
// BEFORE
if (condition) {
    return true;
} else {
    return false;
}

// AFTER
return condition;
```

---

#### 2.12 AvoidBranchingStatementAsLastInLoop (~2 fixes)
**Rule:** Avoid `break`/`continue`/`return` as the only statement in a loop iteration.

**Pattern:**
```java
// BEFORE - return is only thing in loop body sometimes
for (Item item : items) {
    if (matches(item)) {
        return item;  // PMD warning
    }
}

// AFTER - use stream or restructure
return items.stream().filter(this::matches).findFirst().orElse(null);
```

---

#### 2.13 MissingOverride (~1 fix)
**Rule:** Add `@Override` annotation when overriding methods.

**Pattern:**
```java
// BEFORE
public String toString() { ... }

// AFTER
@Override
public String toString() { ... }
```

---

#### 2.14 UnusedPrivateField (~1 fix)
**Rule:** Remove or use private fields that are never read.

**Search:**
```bash
rg 'private.*=' src/main/java --type java
```

---

#### 2.15 UnnecessaryConstructor (~1 fix)
**Rule:** Remove constructors that only call `super()` with no args.

**Pattern:**
```java
// BEFORE
public MyClass() {
    super();
}

// AFTER - delete the constructor entirely
```

---

#### 2.16 UseLocaleWithCaseConversions (~2 remaining)
**Rule:** Use `Locale.ROOT` for case conversions.

Already fixed 10 in previous session. Fix remaining:
```bash
rg '\.toLowerCase\(\)|\.toUpperCase\(\)' src/main/java --type java
```

---

### Priority 2: Medium Effort (~10 fixes)

#### 2.17 MissingStaticMethodInNonInstantiatableClass (~8 fixes)
**Rule:** Classes that can't be instantiated should have static methods or be deleted.

**Analysis needed:** These are likely container classes. Evaluate each:
- If it's a namespace for nested types → acceptable, suppress
- If it should have utility methods → add them or convert to interface

---

#### 2.18 SingularField (~2 fixes)
**Rule:** Field used only in one method should be a local variable.

**Pattern:**
```java
// BEFORE
public class Handler {
    private String tempValue;  // only used in process()

    public void process() {
        tempValue = "...";
        // use tempValue
    }
}

// AFTER
public class Handler {
    public void process() {
        String tempValue = "...";
        // use tempValue
    }
}
```

---

#### 2.19 CloseResource (~1 fix)
**Rule:** Ensure resources are closed (try-with-resources).

**Pattern:**
```java
// BEFORE
Connection conn = getConnection();
// ... use conn
conn.close();

// AFTER
try (Connection conn = getConnection()) {
    // ... use conn
}
```

---

### Priority 3: Skip/Suppress (~160 violations)

#### 2.20 GuardLogStatement (~150 - SKIP)
**Rule:** Wrap expensive log arguments in `if (logger.isXxxEnabled())`.

**Rationale for skipping:**
1. Modern SLF4J with parameterized logging (`{}`) is already efficient
2. Adding guards would significantly increase code verbosity
3. Performance impact is negligible for this application
4. These are in CLI/UI code that isn't performance-critical

**If needed later, add to PMD exclude:**
```xml
<rule ref="category/java/bestpractices.xml/GuardLogStatement">
    <properties>
        <property name="violationSuppressXPath" value="//ClassOrInterfaceDeclaration[contains(@SimpleName, 'Handler') or contains(@SimpleName, 'Controller')]" />
    </properties>
</rule>
```

---

#### 2.21 LooseCoupling (~6 - SKIP)
**Rule:** Use interface types instead of concrete types.

**Rationale for skipping:**
These are in JDBI type handlers (`EnumSetArgumentFactory`, `EnumSetColumnMapper`) which REQUIRE specific `EnumSet` types for proper serialization/deserialization.

---

#### 2.22 AvoidUsingVolatile (~2 - SKIP)
**Rule:** Avoid `volatile` keyword.

**Rationale for skipping:**
If `volatile` is intentionally used for thread safety (e.g., in UI/async code), it should stay. Review each case:
- If used for simple flag → acceptable
- If used for complex synchronization → consider `AtomicReference`

---

## Part 3: Execution Order

### Phase A: IDE Errors - SKIP (Already Resolved)
The visibility issues were stale IDE cache errors. Code compiles successfully.
If IDE still shows errors, run "Java: Clean Java Language Server Workspace".

### Phase B: Priority 1 Quick Wins (30-45 min)
Execute in this order to minimize conflicts:

1. **LiteralsFirstInComparisons** (25 fixes)
   - Use find-replace with regex: `(\w+)\.equals\("([^"]+)"\)` → `"$2".equals($1)`

2. **LambdaCanBeMethodReference** (7 fixes)
   - Manual review required - not all lambdas can be converted

3. **UselessParentheses** (7 fixes)
   - Search: `return \([^()]+\);` and simplify

4. **DanglingJavadoc** (8 fixes)
   - Search for `*/\s+\n\s+\n` (javadoc followed by blank line)

5. **All single-occurrence fixes** (remaining ~15)
   - UnnecessaryFullyQualifiedName
   - UnnecessaryModifier
   - UncommentedEmptyMethodBody
   - EmptyCatchBlock
   - UseUtilityClass
   - SimplifyBooleanReturns
   - AvoidBranchingStatementAsLastInLoop
   - MissingOverride
   - UnusedPrivateField
   - UnnecessaryConstructor
   - UseLocaleWithCaseConversions

### Phase C: Priority 2 Medium Effort (15-20 min)
1. **MissingStaticMethodInNonInstantiatableClass** - evaluate each
2. **SingularField** - refactor to local variables
3. **CloseResource** - add try-with-resources

### Phase D: Verification
```bash
mvn spotless:apply
mvn verify
```

Expected result: PMD violations reduced from 254 to ~160 (GuardLogStatement + LooseCoupling + AvoidUsingVolatile skipped).

---

## Part 4: File-by-File Fix Guide

### CLI Package (`src/main/java/datingapp/cli/`)
| File | Likely Violations |
|------|-------------------|
| EnumMenu.java | GuardLogStatement, LiteralsFirstInComparisons |
| MatchingHandler.java | GuardLogStatement (many), LiteralsFirstInComparisons |
| MessagingHandler.java | GuardLogStatement, LiteralsFirstInComparisons |
| ProfileHandler.java | GuardLogStatement, LiteralsFirstInComparisons |
| RelationshipHandler.java | GuardLogStatement |
| SafetyHandler.java | GuardLogStatement |
| SettingsHandler.java | GuardLogStatement |
| SwipingHandler.java | GuardLogStatement |

### Core Package (`src/main/java/datingapp/core/`)
| File | Likely Violations |
|------|-------------------|
| Dealbreakers.java | MissingStaticMethodInNonInstantiatableClass |
| Messaging.java | MissingStaticMethodInNonInstantiatableClass, DanglingJavadoc |
| Preferences.java | **Interest.Category visibility** |
| Social.java | MissingStaticMethodInNonInstantiatableClass |
| Stats.java | MissingStaticMethodInNonInstantiatableClass |
| UserInteractions.java | MissingStaticMethodInNonInstantiatableClass |
| Various services | SimplifyBooleanReturns, LambdaCanBeMethodReference |

### Storage Package (`src/main/java/datingapp/storage/`)
| File | Likely Violations |
|------|-------------------|
| DatabaseManager.java | CloseResource |
| EnumSetArgumentFactory.java | LooseCoupling (SKIP) |
| EnumSetColumnMapper.java | LooseCoupling (SKIP) |

### UI Package (`src/main/java/datingapp/ui/`)
| File | Likely Violations |
|------|-------------------|
| LoginViewModel.java | AvoidUsingVolatile (SKIP if intentional) |
| Various controllers | EmptyCatchBlock, GuardLogStatement |

---

## Part 5: Validation Checklist

After all fixes:
- [x] `mvn compile` succeeds (no IDE errors) - **Already passing**
- [ ] `mvn spotless:apply` formats code
- [ ] `mvn test` - all 581 tests pass
- [ ] `mvn verify` - build succeeds
- [ ] PMD violations reduced to ~95 (from 254) - after Priority 1+2 fixes
- [ ] No new violations introduced

**Note:** ~160 violations will remain (GuardLogStatement ~150 + LooseCoupling ~6 + AvoidUsingVolatile ~2) - these are intentionally skipped.

---

## Appendix A: PMD Suppression (if needed)

For violations that are intentional, use:
```java
@SuppressWarnings("PMD.RuleName")
public void method() { ... }
```

Or in `pom.xml` exclude entire rules:
```xml
<excludeFromFailureFile>pmd-exclude.properties</excludeFromFailureFile>
```

---

## Appendix B: References

- [PMD Java Rules](https://docs.pmd-code.org/latest/pmd_rules_java.html)
- [CLAUDE.md - Coding Standards](../CLAUDE.md)
- [AGENTS.md - Full Guidelines](../AGENTS.md)
