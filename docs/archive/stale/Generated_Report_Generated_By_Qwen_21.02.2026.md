# AI Agent Code Audit Prompt

## Mission
Perform a deep audit of this Java repository and generate a single Markdown report file at the repo root named:
`Generated_Report_Generated_By_<MODEL>_<DD.MM.YYYY>.md`

Replace `<MODEL>` with your model name (filename-safe: no spaces/slashes; use hyphens/underscores).
Replace `<DD.MM.YYYY>` with today's date in European format (e.g., `21.02.2026`).

**Example:** `Generated_Report_Generated_By_GPT-5.3-Codex-Exhigh_21.02.2026.md`

---

## Scope Rules

### Include (code only)
- `src/main/java/**/*.java` — all production Java source
- `src/test/java/**/*.java` — all test Java source

### Exclude
- `docs/**`, `target/**`, `build/**`
- `*.log`, `*.txt`, `*.md`, `*.json`, `*.xml`, `*.yaml`, `*.yml`
- `resources/**` (config files, FXML, images)
- Any directory outside `src/main/java` and `src/test/java`

**Code is the only source of truth.** Do not cite documentation for architecture claims.

---

## Analysis Requirements

### 1. Architecture Derivation (from code)
- Identify entry points (`Main.java`, `DatingApp.java`, `RestApiServer.java`, etc.)
- Trace dependency injection and service wiring
- Map package boundaries and layer responsibilities
- Extract data flow from method calls and storage interfaces
- Identify domain models, services, and infrastructure from imports and class structure

### 2. Code Quality Analysis
- Run `mvn pmd:check` if available; cite specific PMD violations with line numbers
- Check for `checkstyle` violations if configuration exists
- Identify unused imports, dead code, redundant patterns
- Find exception handling anti-patterns
- Detect thread-safety issues, resource leaks, null-safety gaps

### 3. Testing Coverage Analysis
- Identify untested critical paths
- Check test structure and naming conventions
- Find tests that don't assert anything
- Identify missing edge case coverage

### 4. Optional Command Execution (only if adds insight)
```bash
mvn pmd:check       # Preferred: static analysis
mvn checkstyle:check  # If checkstyle.xml exists
mvn compile         # Verify compilation (optional)
```
Do NOT run the application unless absolutely necessary for understanding.

---

## Findings Format

### Constraints
- **Maximum 30 findings** total
- **Order by severity** (Critical → High → Medium → Low)
- Each finding must be actionable and code-backed

### Required Fields Per Finding

```markdown
### [FINDING-XX] <Short Title>

**Category:** <Missing Feature | Duplication/Redundancy | Logic Flaw | Architecture Issue | Code Quality | Security | Performance | Testing Gap>
**Severity:** <Critical | High | Medium | Low>
**Location:** `[MAIN]` or `[TEST]`
**Impact:** <1-5> (5 = highest business/technical impact)
**Effort:** <1-5> (5 = most effort required)
**Confidence:** <1-5> (5 = certain issue, not speculation)

**File:** `src/main/java/...` or `src/test/java/...`
**Lines:** <start-end> or <line-number>

**Code Excerpt:**
```java
// 3-10 lines showing the issue
```

**Why It Is an Issue:**
<Explain the technical problem clearly>

**Current Negative Impact:**
<What harm does this cause today? Be specific: bugs, maintenance cost, performance, etc.>

**Impact of Fixing:**
<Concrete benefit: reduced bugs, faster builds, easier onboarding, etc.>

**Fix Direction:**
<Specific refactoring or implementation approach>
```

---

## Required Finding Categories (cover all)

1. **Missing Implementations/Features/Capabilities**
   - Incomplete interfaces
   - TODO/FIXME comments without follow-through
   - Advertised features not implemented

2. **Duplication/Redundancy/Simplification Opportunities**
   - Copy-paste code
   - Over-engineered patterns
   - Unused code paths

3. **Logic/Architecture/Structure Flaws**
   - Circular dependencies
   - Layer violations (domain importing infrastructure)
   - God classes, feature envy, inappropriate intimacy

4. **Clear Problems/Issues/Mistakes**
   - Exception swallowing
   - Resource leaks
   - Thread-safety violations
   - Null pointer risks
   - SQL injection risks
   - Hardcoded values

---

## Move-Forward Section (Mandatory)

Add this section after findings:

```markdown
---

## Move-Forward Roadmap

### Phase 1: Quick Wins (1-2 weeks)
<3-5 items with highest impact/effort ratio>

| Item | Value | Effort | Risk | Dependencies |
|------|-------|--------|------|--------------|
| ...  | ...   | Low    | Low  | None         |

### Phase 2: Refactors (1-2 months)
<3-5 structural improvements>

| Item | Value | Effort | Risk   | Dependencies     |
|------|-------|--------|--------|------------------|
| ...  | ...   | Medium | Medium | Phase 1 complete |

### Phase 3: Strategic Improvements (3-6 months)
<3-5 long-term architectural enhancements>

| Item | Value | Effort | Risk | Dependencies        |
|------|-------|--------|------|---------------------|
| ...  | ...   | High   | High | Phases 1-2 complete |

---

## Next Steps (Top 5)
1. ...
2. ...
3. ...
4. ...
5. ...

## What Should Be Implemented But Is Not (Top 5)
1. ...
2. ...
3. ...
4. ...
5. ...

## Missing Features/Components (Top 5)
1. ...
2. ...
3. ...
4. ...
5. ...

## Changes That Improve/Add Value (Top 5)
1. ...
2. ...
3. ...
4. ...
5. ...

## Final Recommendations (Not Listed Above)
1. ...
2. ...
3. ...
```

---

## Report Structure

```markdown
# Code Audit Report

**Generated By:** <MODEL_NAME>
**Date:** <DD.MM.YYYY>
**Repository:** <repo-name>
**Commit Hash:** <git rev-parse HEAD or "N/A">

---

## Executive Summary
<2-3 paragraphs: overall health, critical issues count, top priorities>

---

## Architecture Overview (Code-Derived)
<Describe layers, entry points, data flow, key patterns — all from reading code>

### Entry Points
- ...

### Layer Structure
- ...

### Data Flow
- ...

### Key Patterns
- ...

---

## Methodology
- Analyzed X Java files in `src/main/java`
- Analyzed Y Java files in `src/test/java`
- Ran: `mvn pmd:check` (if available)
- Excluded: docs, target, resources, configs

---

## Findings
<Finding 1 through Finding N (max 30)>

---

## Move-Forward Roadmap
<As specified above>

---

## Appendix: File Inventory
<List all analyzed files with line counts>
```

---

## Quality Gates

### Before Finalizing
- [ ] All 30 findings (or fewer if truly exhaustive) have line references
- [ ] Each finding cites actual code, not speculation
- [ ] Severity ordering is consistent
- [ ] Move-forward section has all 5 sub-sections with 5 items each
- [ ] Architecture section is derived from code, not docs
- [ ] Filename matches pattern exactly
- [ ] Report is in repo root directory

### Prohibited
- ❌ Citing `README.md` or `CLAUDE.md` for architecture claims
- ❌ Findings without line numbers
- ❌ Speculation about "might be an issue"
- ❌ More than 30 findings
- ❌ Modifying source code
- ❌ Running the application unnecessarily

### Required
- ✅ Every finding has: Category, Severity, Location, Impact/Effort/Confidence, File+Lines, Excerpt, Why/Impact/Fix
- ✅ Move-forward roadmap with 3 phases
- ✅ All 5 "Top 5" lists populated
- ✅ Architecture derived from code inspection
- ✅ Filename: `Generated_Report_Generated_By_<MODEL>_<DD.MM.YYYY>.md`

---

## Execution Instructions

1. **Scan the codebase** — Read all Java files in scope
2. **Identify entry points** — Find main classes, bootstrap code
3. **Trace dependencies** — Follow imports, constructor injection, service wiring
4. **Run static analysis** — `mvn pmd:check` if Maven available
5. **Categorize findings** — Sort by severity, cap at 30
6. **Write report** — Follow exact structure above
7. **Save file** — Repo root with correct naming pattern
8. **Verify** — Check all quality gates before finishing

**Do not modify any source files. Generate only the report.**
