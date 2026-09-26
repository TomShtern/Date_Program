# Agent Readiness Improvement Plan
**Repository:** Date_Program
**Current Level:** Level 2 (7/21 criteria, 33%)
**Target Level:** Level 3 (17/21 criteria, 81%)
**Created:** 2026-01-10
**Estimated Time:** 2 hours (Phase 1)

---

## Executive Summary

This plan provides a **concrete roadmap to advance from Level 2 to Level 3** agent readiness, significantly improving AI agent automation capabilities. The focus is on implementing infrastructure that enables autonomous agents to work effectively without human intervention.

### Current State

| Metric      | Value        | Status                           |
|-------------|--------------|----------------------------------|
| **Level 1** | 6/7 (85.7%)  | ⚠️ 1 blocker remaining            |
| **Level 2** | 7/21 (33.3%) | ⚠️ Need 10 more for Level 3       |
| **Level 3** | 6/13 (46.2%) | 🔒 Locked until Level 2 complete |
| **Overall** | 20/54 (37%)  | 📊 Below average readiness       |

### Target State

| Metric      | Value            | Status                     |
|-------------|------------------|----------------------------|
| **Level 1** | 7/7 (100%)       | ✅ Complete                 |
| **Level 2** | 17/21 (81%)      | ✅ Level 3 unlocked         |
| **Level 3** | Ready to improve | 🚀 Next focus area         |
| **Overall** | 30/54 (56%)      | 📈 Above average readiness |

### Impact on AI Agents

**What AI agents gain from these improvements:**

1. **Automated Feedback Loops** - CI/CD gives agents instant validation without waiting for humans
2. **Quality Guardrails** - Coverage thresholds and pre-commit hooks prevent quality degradation
3. **Standardized Outputs** - Templates ensure agent-generated issues/PRs meet expectations
4. **Configuration Clarity** - Environment templates eliminate guesswork
5. **Dependency Security** - Automated updates reduce manual maintenance
6. **Review Automation** - CODEOWNERS ensures appropriate reviewers are assigned

**Bottom line:** Agents can work faster, safer, and more autonomously.

---

## Implementation Strategy

### Phase 1: Quick Wins (No External Dependencies)
**Time:** 1-2 hours
**Complexity:** Low to Medium
**Prerequisites:** None
**Impact:** +8 criteria → 15/21 (71%)

### Phase 2: Install GitHub CLI
**Time:** 5 minutes
**Complexity:** Low
**Prerequisites:** Administrator access
**Impact:** +2 criteria → 17/21 (81%) ✅ **Level 3 achieved!**

### Phase 3: Optional Advanced Features
**Time:** 2-4 hours
**Complexity:** Medium to High
**Prerequisites:** External service accounts
**Impact:** Level 3+ enhancements

---

## Phase 1: Quick Wins

### Item 1: Create .env.example Template ⭐ CRITICAL

**Priority:** HIGHEST (Level 1 blocker!)
**Time:** 5 minutes
**Complexity:** Low

#### Why This Matters for AI Agents
Agents need to know what environment variables are required to run the application. Without this template, they must guess or ask humans for configuration details.

#### What to Create

**File:** `.env.example` (repository root)

```bash
# Dating App Environment Configuration
# Copy this file to .env and update the values

# Database Configuration
# The H2 database password (default: dev)
DATING_APP_DB_PASSWORD=dev

# Database Connection
# Location of the H2 database file (default: ./data/dating)
DATING_APP_DB_PATH=./data/dating

# Application Settings
# Maximum distance for candidate matching in kilometers (default: 50)
# MAX_DISTANCE_KM=50

# Age Range
# Default min/max age for user preferences (default: 18, 99)
# MIN_AGE=18
# MAX_AGE=99
```

#### Verification Steps

```bash
# 1. Verify file exists
ls .env.example

# 2. Check content
cat .env.example

# 3. Test that agents can parse it
grep "DATING_APP_DB_PASSWORD" .env.example
```

#### Success Criteria
- ✅ `.env.example` exists in repository root
- ✅ Contains `DATING_APP_DB_PASSWORD` variable
- ✅ Includes helpful comments
- ✅ Matches actual environment variables used in code

#### Agent Readiness Impact
- **env_template:** ❌ → ✅ (+1 criterion)
- **Level 1:** 6/7 → 7/7 (100%) ✅ **Complete!**

---

### Item 2: Add JaCoCo Test Coverage Plugin

**Priority:** HIGH
**Time:** 10 minutes
**Complexity:** Low

#### Why This Matters for AI Agents
Coverage thresholds prevent agents from reducing test quality. Without enforcement, agents might skip writing tests or delete existing tests without knowing the impact.

#### What to Modify

**File:** `pom.xml`

**Location:** Add new plugin AFTER the PMD plugin, BEFORE the closing `</plugins>` tag

```xml
            <!-- JaCoCo: Test coverage enforcement -->
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>0.8.11</version>
                <executions>
                    <!-- Prepare agent for test execution -->
                    <execution>
                        <id>prepare-agent</id>
                        <goals>
                            <goal>prepare-agent</goal>
                        </goals>
                    </execution>
                    <!-- Generate coverage report -->
                    <execution>
                        <id>report</id>
                        <phase>test</phase>
                        <goals>
                            <goal>report</goal>
                        </goals>
                    </execution>
                    <!-- Enforce coverage thresholds -->
                    <execution>
                        <id>check</id>
                        <phase>verify</phase>
                        <goals>
                            <goal>check</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <rule>
                                    <element>BUNDLE</element>
                                    <limits>
                                        <limit>
                                            <counter>LINE</counter>
                                            <value>COVEREDRATIO</value>
                                            <minimum>0.80</minimum>
                                        </limit>
                                        <limit>
                                            <counter>BRANCH</counter>
                                            <value>COVEREDRATIO</value>
                                            <minimum>0.75</minimum>
                                        </limit>
                                    </limits>
                                </rule>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
```

#### Verification Steps

```bash
# 1. Verify plugin is configured
mvn help:effective-pom | grep -A 5 jacoco

# 2. Run tests with coverage
mvn clean test

# 3. Check coverage report was generated
ls target/site/jacoco/index.html

# 4. View coverage report (open in browser)
start target/site/jacoco/index.html  # Windows
# open target/site/jacoco/index.html  # macOS
# xdg-open target/site/jacoco/index.html  # Linux

# 5. Verify coverage enforcement
mvn verify
```

#### Adjusting Coverage Thresholds

If current coverage is below 80%, you can temporarily lower thresholds:

```xml
<minimum>0.70</minimum>  <!-- Start at 70% if needed -->
```

**Goal:** Gradually increase to 80%+ as tests are added.

#### Success Criteria
- ✅ JaCoCo plugin added to pom.xml
- ✅ Coverage report generated in `target/site/jacoco/`
- ✅ `mvn verify` enforces minimum thresholds
- ✅ Build fails if coverage drops below threshold

#### Agent Readiness Impact
- **test_coverage_thresholds:** ❌ → ✅ (+1 criterion)

---

### Item 3: Create GitHub Issue Templates

**Priority:** HIGH
**Time:** 15 minutes
**Complexity:** Low

#### Why This Matters for AI Agents
Templates standardize how agents report bugs and request features. Without templates, agent-generated issues lack structure and may be unclear or incomplete.

#### What to Create

**Directory:** `.github/ISSUE_TEMPLATE/`

**File 1:** `.github/ISSUE_TEMPLATE/bug_report.md`

```markdown
---
name: Bug Report
about: Report a bug or unexpected behavior
title: '[BUG] '
labels: bug
assignees: ''
---

## Bug Description
A clear and concise description of what the bug is.

## Steps to Reproduce
1. Go to '...'
2. Execute command '...'
3. Observe error '...'

## Expected Behavior
What should have happened.

## Actual Behavior
What actually happened.

## Environment
- **Java Version:** [e.g., Java 21]
- **Maven Version:** [e.g., 3.9.6]
- **OS:** [e.g., Windows 11, macOS 14, Ubuntu 22.04]
- **Database:** H2 [version from pom.xml]

## Stack Trace / Logs
```
Paste relevant error logs or stack traces here
```

## Test Case (Optional)
```java
@Test
void reproduceBug() {
    // Test that demonstrates the bug
}
```

## Additional Context
Any other information about the problem.

## Related Issues
- #123
- #456
```

**File 2:** `.github/ISSUE_TEMPLATE/feature_request.md`

```markdown
---
name: Feature Request
about: Suggest an idea or enhancement
title: '[FEATURE] '
labels: enhancement
assignees: ''
---

## Feature Description
A clear and concise description of the feature.

## Problem Statement
What problem does this feature solve?

**Example:** "As a [user type], I want [goal] so that [benefit]."

## Proposed Solution
Describe how you envision the feature working.

## Alternatives Considered
Other approaches you've thought about.

## Implementation Details (Optional)
### Core Layer
- [ ] Add new domain models
- [ ] Add new services
- [ ] Modify existing services

### Storage Layer
- [ ] Add new storage interface
- [ ] Add H2 implementation
- [ ] Modify database schema

### CLI Layer
- [ ] Add new handler
- [ ] Modify existing menu
- [ ] Add new commands

## Test Plan
How should this feature be tested?

## Success Criteria
- [ ] Criterion 1
- [ ] Criterion 2
- [ ] Criterion 3

## Related Issues
- #123
- #456

## Additional Context
Any other information or screenshots.
```

**File 3:** `.github/ISSUE_TEMPLATE/config.yml`

```yaml
blank_issues_enabled: false
contact_links:
  - name: Documentation
    url: https://github.com/TomShtern/Date_Program/blob/main/CLAUDE.md
    about: Read the project documentation (CLAUDE.md, AGENTS.md)
  - name: Ask a Question
    url: https://github.com/TomShtern/Date_Program/discussions
    about: Ask questions or discuss ideas
```

#### Verification Steps

```bash
# 1. Verify directory exists
ls -la .github/ISSUE_TEMPLATE/

# 2. Check files
ls .github/ISSUE_TEMPLATE/
# Should see: bug_report.md, feature_request.md, config.yml

# 3. Verify YAML frontmatter
head -10 .github/ISSUE_TEMPLATE/bug_report.md

# 4. Test on GitHub (after commit)
# Go to: https://github.com/TomShtern/Date_Program/issues/new/choose
# Verify templates appear
```

#### Success Criteria
- ✅ `.github/ISSUE_TEMPLATE/` directory exists
- ✅ `bug_report.md` with structured format
- ✅ `feature_request.md` with user story format
- ✅ `config.yml` to disable blank issues
- ✅ Templates visible on GitHub when creating new issue

#### Agent Readiness Impact
- **issue_templates:** ❌ → ✅ (+1 criterion)

---

### Item 4: Create Pull Request Template

**Priority:** HIGH
**Time:** 5 minutes
**Complexity:** Low

#### Why This Matters for AI Agents
PR templates ensure agents include all necessary information when submitting code changes. This reduces back-and-forth with reviewers and speeds up the review process.

#### What to Create

**File:** `.github/pull_request_template.md`

```markdown
## Description
<!-- Brief description of what this PR does -->

## Type of Change
<!-- Mark relevant items with [x] -->

- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to change)
- [ ] Refactoring (no functional changes, code improvements)
- [ ] Documentation update
- [ ] Test coverage improvement
- [ ] Performance optimization

## Related Issues
<!-- Link related issues using #123 syntax -->

Closes #
Relates to #

## Changes Made

### Core Layer
- [ ] Modified domain models
- [ ] Added/modified services
- [ ] Updated storage interfaces
- [ ] Other: _______

### Storage Layer
- [ ] Added H2 storage implementation
- [ ] Modified database schema
- [ ] Updated DatabaseManager
- [ ] Other: _______

### CLI Layer
- [ ] Modified handlers
- [ ] Updated menu system
- [ ] Added new commands
- [ ] Other: _______

## Testing

### Test Coverage
```bash
# Run tests with coverage
mvn clean test jacoco:report

# Current coverage: __%
```

### Tests Added/Modified
- [ ] Unit tests added
- [ ] Integration tests added
- [ ] Existing tests updated
- [ ] All tests passing

### Manual Testing
<!-- Describe manual testing performed -->

1. Tested scenario A: ✅ Pass
2. Tested scenario B: ✅ Pass
3. Tested edge case C: ✅ Pass

## Code Quality

### Pre-submission Checklist
- [ ] Code formatted with Spotless (`mvn spotless:apply`)
- [ ] Checkstyle passed (`mvn checkstyle:check`)
- [ ] PMD analysis passed (`mvn pmd:check`)
- [ ] All tests passing (`mvn test`)
- [ ] Coverage thresholds met (`mvn verify`)
- [ ] No new warnings introduced

### Architecture Compliance
- [ ] Core layer has zero framework imports
- [ ] Services use dependency injection
- [ ] Storage interfaces defined in core
- [ ] State machines follow established patterns
- [ ] Immutable data uses records
- [ ] Validation in constructors/setters

## Documentation

- [ ] CLAUDE.md updated (if needed)
- [ ] AGENTS.md updated (if needed)
- [ ] JavaDoc added for public APIs
- [ ] Architecture diagrams updated (if needed)
- [ ] README.md updated (if needed)

## Breaking Changes
<!-- If applicable, describe breaking changes and migration path -->

None / Describe here

## Performance Impact
<!-- If applicable, describe performance implications -->

No impact / Describe here

## Security Considerations
<!-- If applicable, describe security implications -->

No impact / Describe here

## Screenshots / Videos
<!-- If applicable, add screenshots or screen recordings -->

## Deployment Notes
<!-- Any special deployment considerations -->

None / Describe here

## Reviewer Notes
<!-- Anything specific reviewers should focus on -->

## Checklist for Reviewers

- [ ] Code follows project conventions
- [ ] Logic is sound and efficient
- [ ] Tests adequately cover changes
- [ ] Documentation is clear and complete
- [ ] No obvious security issues
- [ ] Performance is acceptable
```

#### Verification Steps

```bash
# 1. Verify file exists
ls .github/pull_request_template.md

# 2. Check content
cat .github/pull_request_template.md

# 3. Test on GitHub (after commit + push)
# Create a new PR and verify template auto-populates
```

#### Success Criteria
- ✅ `.github/pull_request_template.md` exists
- ✅ Contains comprehensive checklist
- ✅ Includes architecture compliance section
- ✅ Covers all three layers (core, storage, cli)
- ✅ Template auto-populates when creating PR

#### Agent Readiness Impact
- **pr_templates:** ❌ → ✅ (+1 criterion)

---

### Item 5: Add CODEOWNERS File

**Priority:** MEDIUM
**Time:** 5 minutes
**Complexity:** Low

#### Why This Matters for AI Agents
CODEOWNERS automates review assignment. When agents create PRs, the appropriate reviewers are automatically notified without manual assignment.

#### What to Create

**File:** `.github/CODEOWNERS`

```
# Code Owners for Dating App
# These owners will be requested for review when PRs touch these files

# Default owner for everything
* @TomShtern

# Core business logic
/src/main/java/datingapp/core/ @TomShtern

# Storage layer
/src/main/java/datingapp/storage/ @TomShtern

# CLI layer
/src/main/java/datingapp/cli/ @TomShtern

# Tests
/src/test/java/datingapp/ @TomShtern

# Build configuration
pom.xml @TomShtern
.github/ @TomShtern

# Documentation
README.md @TomShtern
CLAUDE.md @TomShtern
AGENTS.md @TomShtern
/docs/ @TomShtern

# Database schema changes (require extra attention)
/src/main/java/datingapp/storage/DatabaseManager.java @TomShtern
```

#### Future Enhancement (Multiple Owners)

When your team grows, you can assign different owners:

```
# Example with team
/src/main/java/datingapp/core/ @TomShtern @backend-team
/src/main/java/datingapp/cli/ @TomShtern @frontend-team
/docs/ @TomShtern @documentation-team
```

#### Verification Steps

```bash
# 1. Verify file exists
ls .github/CODEOWNERS

# 2. Check syntax
cat .github/CODEOWNERS

# 3. Test on GitHub (after commit + push)
# Create a PR and verify @TomShtern is auto-requested for review
```

#### Success Criteria
- ✅ `.github/CODEOWNERS` exists
- ✅ Contains ownership rules for all main directories
- ✅ Uses valid GitHub username format (@TomShtern)
- ✅ Automatic review requests work on PRs

#### Agent Readiness Impact
- **codeowners:** ❌ → ✅ (+1 criterion)

---

### Item 6: Configure Dependabot

**Priority:** MEDIUM
**Time:** 5 minutes
**Complexity:** Low

#### Why This Matters for AI Agents
Dependabot automatically creates PRs for dependency updates. This keeps the project secure and up-to-date without manual monitoring of Maven dependencies.

#### What to Create

**File:** `.github/dependabot.yml`

```yaml
version: 2
updates:
  # Maven dependencies
  - package-ecosystem: "maven"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "09:00"
    open-pull-requests-limit: 5
    labels:
      - "dependencies"
      - "automated"
    commit-message:
      prefix: "chore"
      include: "scope"
    reviewers:
      - "TomShtern"
    assignees:
      - "TomShtern"
    # Group updates to reduce PR noise
    groups:
      logging:
        patterns:
          - "org.slf4j:*"
          - "ch.qos.logback:*"
      testing:
        patterns:
          - "org.junit.jupiter:*"
      build-tools:
        patterns:
          - "org.apache.maven.plugins:*"
          - "com.diffplug.spotless:*"
          - "org.codehaus.mojo:*"
    ignore:
      # Pin Java to specific version
      - dependency-name: "maven-compiler-plugin"
        update-types: ["version-update:semver-major"]

  # GitHub Actions dependencies
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "09:00"
    open-pull-requests-limit: 3
    labels:
      - "dependencies"
      - "github-actions"
    commit-message:
      prefix: "ci"
```

#### Verification Steps

```bash
# 1. Verify file exists
ls .github/dependabot.yml

# 2. Validate YAML syntax
# Use online validator: https://www.yamllint.com/
cat .github/dependabot.yml

# 3. Test on GitHub (after commit + push)
# Go to: https://github.com/TomShtern/Date_Program/security/dependabot
# Verify Dependabot is enabled

# 4. Check for updates (GitHub will start checking within 24 hours)
# Go to: Insights > Dependency graph > Dependabot
```

#### Configuration Options

**Update Frequency:**
- `"daily"` - Check every day (not recommended, too many PRs)
- `"weekly"` - Check once per week ✅ **Recommended**
- `"monthly"` - Check once per month

**PR Limits:**
- `open-pull-requests-limit: 5` - Max 5 open PRs at once
- Adjust based on your capacity to review

#### Success Criteria
- ✅ `.github/dependabot.yml` exists with valid YAML
- ✅ Maven ecosystem configured
- ✅ Weekly schedule set
- ✅ PR grouping configured to reduce noise
- ✅ Dependabot creates PRs automatically (verify after 24 hours)

#### Agent Readiness Impact
- **dependency_update_automation:** ❌ → ✅ (+1 criterion)

---

### Item 7: Create Pre-commit Hook Template

**Priority:** MEDIUM
**Time:** 15 minutes
**Complexity:** Low

#### Why This Matters for AI Agents
Pre-commit hooks catch formatting and linting issues BEFORE code reaches CI/CD. This provides instant feedback and prevents failed builds from trivial issues.

#### What to Create

**File 1:** `scripts/install-hooks.sh` (Unix/macOS/Linux)

```bash
#!/bin/bash
# Install Git hooks for the Dating App project

set -e

echo "Installing Git hooks..."

# Create hooks directory if it doesn't exist
mkdir -p .git/hooks

# Create pre-commit hook
cat > .git/hooks/pre-commit << 'EOF'
#!/bin/bash
# Pre-commit hook for Dating App
# Runs code quality checks before allowing commit

set -e

echo "🔍 Running pre-commit checks..."

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not found. Please install Maven first."
    exit 1
fi

# 1. Format check (Spotless)
echo "📝 Checking code formatting..."
if ! mvn spotless:check -q; then
    echo "❌ Code formatting issues found!"
    echo "💡 Run 'mvn spotless:apply' to fix formatting"
    exit 1
fi
echo "✅ Formatting check passed"

# 2. Linting (Checkstyle)
echo "🔎 Running Checkstyle..."
if ! mvn checkstyle:check -q; then
    echo "❌ Checkstyle violations found!"
    echo "💡 Check output above for details"
    exit 1
fi
echo "✅ Checkstyle passed"

# 3. Code quality (PMD) - warnings only, don't fail
echo "🔬 Running PMD analysis..."
mvn pmd:check -q || echo "⚠️  PMD warnings (not blocking commit)"

# 4. Tests (quick smoke test - skip integration tests)
echo "🧪 Running unit tests..."
if ! mvn test -DskipITs -q; then
    echo "❌ Tests failed!"
    echo "💡 Fix failing tests before committing"
    exit 1
fi
echo "✅ Tests passed"

echo "✨ All pre-commit checks passed! Proceeding with commit..."
EOF

# Make hook executable
chmod +x .git/hooks/pre-commit

echo "✅ Git hooks installed successfully!"
echo ""
echo "Pre-commit hook will run:"
echo "  1. Code formatting check (Spotless)"
echo "  2. Linting (Checkstyle)"
echo "  3. Code quality analysis (PMD)"
echo "  4. Unit tests"
echo ""
echo "To skip hooks temporarily (not recommended):"
echo "  git commit --no-verify"
```

**File 2:** `scripts/install-hooks.ps1` (Windows PowerShell)

```powershell
# Install Git hooks for the Dating App project
# Run with: .\scripts\install-hooks.ps1

Write-Host "Installing Git hooks..." -ForegroundColor Cyan

# Create hooks directory if it doesn't exist
New-Item -ItemType Directory -Force -Path .git\hooks | Out-Null

# Create pre-commit hook
$hookContent = @'
#!/bin/bash
# Pre-commit hook for Dating App
# Runs code quality checks before allowing commit

set -e

echo "🔍 Running pre-commit checks..."

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not found. Please install Maven first."
    exit 1
fi

# 1. Format check (Spotless)
echo "📝 Checking code formatting..."
if ! mvn spotless:check -q; then
    echo "❌ Code formatting issues found!"
    echo "💡 Run 'mvn spotless:apply' to fix formatting"
    exit 1
fi
echo "✅ Formatting check passed"

# 2. Linting (Checkstyle)
echo "🔎 Running Checkstyle..."
if ! mvn checkstyle:check -q; then
    echo "❌ Checkstyle violations found!"
    echo "💡 Check output above for details"
    exit 1
fi
echo "✅ Checkstyle passed"

# 3. Code quality (PMD) - warnings only, don't fail
echo "🔬 Running PMD analysis..."
mvn pmd:check -q || echo "⚠️  PMD warnings (not blocking commit)"

# 4. Tests (quick smoke test - skip integration tests)
echo "🧪 Running unit tests..."
if ! mvn test -DskipITs -q; then
    echo "❌ Tests failed!"
    echo "💡 Fix failing tests before committing"
    exit 1
fi
echo "✅ Tests passed"

echo "✨ All pre-commit checks passed! Proceeding with commit..."
'@

Set-Content -Path .git\hooks\pre-commit -Value $hookContent -NoNewline

Write-Host "✅ Git hooks installed successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "Pre-commit hook will run:" -ForegroundColor Yellow
Write-Host "  1. Code formatting check (Spotless)"
Write-Host "  2. Linting (Checkstyle)"
Write-Host "  3. Code quality analysis (PMD)"
Write-Host "  4. Unit tests"
Write-Host ""
Write-Host "To skip hooks temporarily (not recommended):" -ForegroundColor Yellow
Write-Host "  git commit --no-verify"
```

#### Installation Instructions

**For Unix/macOS/Linux:**
```bash
# Make script executable
chmod +x scripts/install-hooks.sh

# Run installation script
./scripts/install-hooks.sh
```

**For Windows (PowerShell):**
```powershell
# Run installation script
.\scripts\install-hooks.ps1
```

**Manual Installation (any OS):**
```bash
# Copy hook content manually
cat scripts/pre-commit > .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit  # Unix only
```

#### Verification Steps

```bash
# 1. Verify scripts exist
ls scripts/install-hooks.*

# 2. Install hooks
./scripts/install-hooks.sh  # Unix
# OR
.\scripts\install-hooks.ps1  # Windows

# 3. Verify hook is installed
ls -la .git/hooks/pre-commit

# 4. Test the hook
# Make a small change and try to commit
echo "# test" >> README.md
git add README.md
git commit -m "test: verify pre-commit hook"
# Should see checks running

# 5. Revert test change
git reset HEAD~1
git checkout README.md
```

#### Customization Options

**Faster Hook (Skip Tests):**
Comment out or remove the test section for faster commits:
```bash
# # 4. Tests (quick smoke test)
# echo "🧪 Running unit tests..."
# ...
```

**Stricter Hook (Fail on PMD):**
Change PMD to block commits:
```bash
if ! mvn pmd:check -q; then
    echo "❌ PMD violations found!"
    exit 1
fi
```

#### Success Criteria
- ✅ `scripts/install-hooks.sh` exists (Unix)
- ✅ `scripts/install-hooks.ps1` exists (Windows)
- ✅ Installation script creates `.git/hooks/pre-commit`
- ✅ Hook is executable
- ✅ Hook runs on `git commit`
- ✅ Hook blocks commits with formatting issues
- ✅ Hook can be bypassed with `--no-verify` if needed

#### Agent Readiness Impact
- **pre_commit_hooks:** ❌ → ✅ (+1 criterion)

---

### Item 8: Create GitHub Actions CI/CD Pipeline ⭐ HIGHEST IMPACT

**Priority:** HIGHEST
**Time:** 20 minutes
**Complexity:** Medium

#### Why This Matters for AI Agents
CI/CD is the **most critical piece** for agent automation. It provides instant, automated feedback on every code change without human intervention. This is the foundation for all other automation.

#### What to Create

**File:** `.github/workflows/ci.yml`

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    name: Build and Test
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'

      - name: Cache Maven packages
        uses: actions/cache@v3
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
          restore-keys: |
            ${{ runner.os }}-maven-

      - name: Compile
        run: mvn compile -B

      - name: Run tests
        run: mvn test -B

      - name: Generate coverage report
        run: mvn jacoco:report -B

      - name: Upload coverage reports
        uses: codecov/codecov-action@v3
        with:
          files: ./target/site/jacoco/jacoco.xml
          flags: unittests
          name: codecov-umbrella
          fail_ci_if_error: false

      - name: Check code formatting (Spotless)
        run: mvn spotless:check -B

      - name: Run Checkstyle
        run: mvn checkstyle:check -B

      - name: Run PMD
        run: mvn pmd:check -B
        continue-on-error: true

      - name: Verify coverage thresholds
        run: mvn verify -B

      - name: Build JAR
        run: mvn package -DskipTests -B

      - name: Upload build artifact
        uses: actions/upload-artifact@v3
        with:
          name: dating-app-jar
          path: target/dating-app-*.jar
          retention-days: 7

  code-quality:
    name: Code Quality Analysis
    runs-on: ubuntu-latest
    needs: build

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'

      - name: Run full PMD analysis
        run: mvn pmd:pmd -B

      - name: Upload PMD report
        uses: actions/upload-artifact@v3
        with:
          name: pmd-report
          path: target/site/pmd.html
          retention-days: 7

  security-scan:
    name: Security Scanning
    runs-on: ubuntu-latest
    needs: build

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'

      - name: Run dependency check
        run: mvn dependency:analyze -B

      - name: Check for vulnerabilities (OWASP)
        run: mvn org.owasp:dependency-check-maven:check -B || true
        continue-on-error: true
```

#### Advanced Version (With Status Badges)

Add this to your `README.md` after CI/CD is working:

```markdown
# Dating App CLI

[![CI/CD](https://github.com/TomShtern/Date_Program/actions/workflows/ci.yml/badge.svg)](https://github.com/TomShtern/Date_Program/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/TomShtern/Date_Program/branch/main/graph/badge.svg)](https://codecov.io/gh/TomShtern/Date_Program)
```

#### Verification Steps

```bash
# 1. Verify workflow file exists
ls .github/workflows/ci.yml

# 2. Validate YAML syntax
# Use: https://www.yamllint.com/
cat .github/workflows/ci.yml

# 3. Commit and push to trigger CI
git add .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions CI/CD pipeline"
git push

# 4. Check workflow status on GitHub
# Go to: https://github.com/TomShtern/Date_Program/actions

# 5. Verify all jobs pass
# Build → Code Quality → Security Scan should all succeed
```

#### Troubleshooting Common Issues

**Issue 1: Tests fail in CI but pass locally**
- Solution: Check database file paths, ensure H2 uses in-memory mode for tests

**Issue 2: Coverage threshold too high**
- Solution: Lower threshold temporarily in pom.xml:
  ```xml
  <minimum>0.70</minimum>  <!-- Instead of 0.80 -->
  ```

**Issue 3: Slow CI builds**
- Solution: Already using Maven caching, consider:
  - Parallel test execution
  - Skip integration tests in PR checks (run only on main)

**Issue 4: PMD failing too often**
- Solution: Set `continue-on-error: true` to make PMD non-blocking

#### CI/CD Workflow Explanation

**Jobs:**

1. **Build** - Core validation
   - Compile code
   - Run tests
   - Generate coverage
   - Check formatting
   - Run linters
   - Verify thresholds
   - Build JAR

2. **Code Quality** - Deep analysis
   - Run full PMD scan
   - Upload reports

3. **Security Scan** - Dependency checks
   - Analyze dependencies
   - Check for vulnerabilities

**Triggers:**
- Push to `main` branch
- Pull requests to `main`
- Manual trigger (`workflow_dispatch`)

#### Success Criteria
- ✅ `.github/workflows/ci.yml` exists with valid YAML
- ✅ Workflow triggers on push and PR
- ✅ All jobs complete successfully
- ✅ Coverage reports uploaded
- ✅ Build artifacts saved
- ✅ Status checks appear on PRs
- ✅ Failed checks block PR merging (if branch protection enabled)

#### Agent Readiness Impact
- **pre_commit_hooks:** ❌ → ✅ (+1 criterion, if not already counted)
- **Enables future criteria:**
  - `automated_pr_review` (when branch protection added)
  - `automated_security_review` (dependency scanning)
  - `fast_ci_feedback` (Level 4 criterion)

---

## Phase 1 Summary

### What You've Accomplished

After completing Phase 1, you will have:

✅ **8 new infrastructure components:**
1. Environment template (`.env.example`)
2. Test coverage enforcement (JaCoCo in pom.xml)
3. Issue templates (bug + feature)
4. PR template
5. CODEOWNERS file
6. Dependabot configuration
7. Pre-commit hooks (installation script)
8. CI/CD pipeline (GitHub Actions)

✅ **Score improvement:**
- Level 1: 6/7 → 7/7 (100%) ✅
- Level 2: 7/21 → 15/21 (71%)
- Overall: 20/54 → 28/54 (52%)

### What's Still Needed for Level 3

You're at 15/21 (71%), need 17/21 (81%) for Level 3. Missing:

1. **vcs_cli_tools** - Install GitHub CLI (Phase 2)
2. **One more criterion** - Could be:
   - `devcontainer` (add .devcontainer.json)
   - `local_services_setup` (add docker-compose.yml)
   - `automated_doc_generation` (add JavaDoc generation)

---

## Phase 2: Install GitHub CLI

**Priority:** HIGH
**Time:** 5 minutes
**Complexity:** Low
**Prerequisites:** Administrator access

### Why This Matters

GitHub CLI unlocks several automated checks that are currently skipped:
- `automated_pr_review`
- `automated_security_review`
- `branch_protection`
- `secret_scanning`

### Installation Instructions

#### Windows
```powershell
# Using winget (Windows 11)
winget install --id GitHub.cli

# Using Scoop
scoop install gh

# Using Chocolatey
choco install gh
```

#### macOS
```bash
# Using Homebrew
brew install gh
```

#### Linux
```bash
# Debian/Ubuntu
type -p curl >/dev/null || sudo apt install curl -y
curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | sudo dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg
sudo chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | sudo tee /etc/apt/sources.list.d/github-cli.list > /dev/null
sudo apt update
sudo apt install gh -y

# Fedora/RHEL
sudo dnf install gh
```

### Authentication

```bash
# Authenticate with GitHub
gh auth login

# Verify authentication
gh auth status

# Test CLI
gh repo view TomShtern/Date_Program
```

### Verification Steps

```bash
# 1. Check installation
gh --version

# 2. Verify authentication
gh auth status

# 3. Test access to your repo
gh repo view TomShtern/Date_Program --json name,owner

# 4. List open PRs (if any)
gh pr list

# 5. Check issues
gh issue list
```

### Success Criteria
- ✅ `gh` command available
- ✅ Authenticated with GitHub account
- ✅ Can access Date_Program repository
- ✅ Can list issues and PRs

### Agent Readiness Impact
- **vcs_cli_tools:** ❌ → ✅ (+1 criterion)
- **Unlocks 3 skipped criteria** for future evaluation

---

## Phase 3: Optional Advanced Features

These are **optional enhancements** for Level 3+ maturity. Implement only if you need them.

### Option 1: Add Devcontainer

**Time:** 15 minutes
**Benefit:** Reproducible development environment

**File:** `.devcontainer/devcontainer.json`

```json
{
  "name": "Dating App Development",
  "image": "mcr.microsoft.com/devcontainers/java:21",
  "features": {
    "ghcr.io/devcontainers/features/java:1": {
      "version": "21",
      "installMaven": "true"
    }
  },
  "customizations": {
    "vscode": {
      "extensions": [
        "vscjava.vscode-java-pack",
        "vscjava.vscode-maven",
        "sonarsource.sonarlint-vscode",
        "redhat.java"
      ],
      "settings": {
        "java.configuration.runtimes": [
          {
            "name": "JavaSE-21",
            "path": "/usr/local/sdkman/candidates/java/current",
            "default": true
          }
        ]
      }
    }
  },
  "postCreateCommand": "mvn compile",
  "forwardPorts": [],
  "mounts": [
    "source=${localWorkspaceFolder}/data,target=/workspace/data,type=bind,consistency=cached"
  ]
}
```

### Option 2: Add Docker Compose for Local Services

**Time:** 10 minutes
**Benefit:** Standardized local setup

**File:** `docker-compose.yml`

```yaml
version: '3.8'

services:
  # Future: When moving to PostgreSQL or other services
  # postgres:
  #   image: postgres:16
  #   environment:
  #     POSTGRES_USER: dating_app
  #     POSTGRES_PASSWORD: ${DATING_APP_DB_PASSWORD}
  #     POSTGRES_DB: dating_db
  #   ports:
  #     - "5432:5432"
  #   volumes:
  #     - postgres_data:/var/lib/postgresql/data

  # Currently using embedded H2, so this is a placeholder
  # for future services (Redis, message queue, etc.)

volumes:
  postgres_data:
```

### Option 3: Integrate Error Tracking (Sentry)

**Time:** 30 minutes
**Benefit:** Production error monitoring
**Prerequisites:** Sentry account (free tier available)

**Steps:**
1. Sign up at https://sentry.io/
2. Create new Java project
3. Get DSN (Data Source Name)
4. Add Sentry SDK to pom.xml:
   ```xml
   <dependency>
       <groupId>io.sentry</groupId>
       <artifactId>sentry</artifactId>
       <version>7.0.0</version>
   </dependency>
   ```
5. Configure in code:
   ```java
   Sentry.init(options -> {
       options.setDsn(System.getenv("SENTRY_DSN"));
       options.setEnvironment("production");
   });
   ```

---

## Expected Outcomes

### Score Progression

| Phase             | Level 1      | Level 2         | Level 3        | Overall       |
|-------------------|--------------|-----------------|----------------|---------------|
| **Before**        | 6/7 (85.7%)  | 7/21 (33.3%)    | 6/13 (46.2%)   | 20/54 (37%)   |
| **After Phase 1** | 7/7 (100%) ✅ | 15/21 (71.4%)   | 6/13 (46.2%)   | 28/54 (52%)   |
| **After Phase 2** | 7/7 (100%) ✅ | 17/21 (81.0%) ✅ | Level unlocked | 30/54 (56%)   |
| **After Phase 3** | 7/7 (100%) ✅ | 19/21 (90.5%) ✅ | Improvements   | 32+/54 (59%+) |

### Criteria Added

**Phase 1:** 8 criteria
- env_template ✅
- test_coverage_thresholds ✅
- issue_templates ✅
- pr_templates ✅
- codeowners ✅
- dependency_update_automation ✅
- pre_commit_hooks ✅
- CI/CD (enables multiple)

**Phase 2:** 2+ criteria
- vcs_cli_tools ✅
- Unlocks skipped criteria

**Phase 3:** Variable
- devcontainer
- local_services_setup
- error_tracking_contextualized

---

## Implementation Checklist

### Phase 1: Quick Wins

- [ ] **Item 1:** Create `.env.example` template
  - [ ] File created in repository root
  - [ ] Contains DATING_APP_DB_PASSWORD
  - [ ] Verified with `cat .env.example`

- [ ] **Item 2:** Add JaCoCo coverage plugin
  - [ ] Plugin added to pom.xml
  - [ ] Coverage report generates: `mvn test`
  - [ ] Thresholds enforce: `mvn verify`
  - [ ] Report viewable: `target/site/jacoco/index.html`

- [ ] **Item 3:** Create issue templates
  - [ ] Directory created: `.github/ISSUE_TEMPLATE/`
  - [ ] Bug report template exists
  - [ ] Feature request template exists
  - [ ] Config.yml exists
  - [ ] Templates visible on GitHub

- [ ] **Item 4:** Create PR template
  - [ ] File created: `.github/pull_request_template.md`
  - [ ] Contains comprehensive checklist
  - [ ] Auto-populates on new PR

- [ ] **Item 5:** Add CODEOWNERS
  - [ ] File created: `.github/CODEOWNERS`
  - [ ] Contains ownership rules
  - [ ] Auto-assigns reviewers on PR

- [ ] **Item 6:** Configure Dependabot
  - [ ] File created: `.github/dependabot.yml`
  - [ ] Maven ecosystem configured
  - [ ] Weekly schedule set
  - [ ] Creates PRs automatically

- [ ] **Item 7:** Create pre-commit hooks
  - [ ] Script created: `scripts/install-hooks.sh`
  - [ ] Script created: `scripts/install-hooks.ps1`
  - [ ] Hook installed: `.git/hooks/pre-commit`
  - [ ] Hook runs on commit
  - [ ] Hook blocks bad code

- [ ] **Item 8:** Create CI/CD pipeline
  - [ ] Workflow created: `.github/workflows/ci.yml`
  - [ ] Workflow triggers on push/PR
  - [ ] All jobs pass
  - [ ] Coverage uploaded
  - [ ] Artifacts saved

### Phase 2: GitHub CLI

- [ ] Install GitHub CLI (`gh`)
- [ ] Authenticate: `gh auth login`
- [ ] Verify: `gh auth status`
- [ ] Test repo access: `gh repo view`

### Phase 3: Optional (Pick as needed)

- [ ] Add devcontainer configuration
- [ ] Add docker-compose.yml
- [ ] Integrate Sentry error tracking
- [ ] Add more observability tools

---

## Testing & Validation

### Automated Tests

```bash
# Run full validation suite
mvn clean verify

# Expected output:
# ✅ Compilation successful
# ✅ All tests pass (29+ test classes)
# ✅ Coverage meets thresholds (80% line, 75% branch)
# ✅ Spotless formatting correct
# ✅ Checkstyle passes
# ✅ PMD analysis complete
```

### Manual Verification

**1. Environment Template**
```bash
cat .env.example | grep DATING_APP_DB_PASSWORD
# Should show: DATING_APP_DB_PASSWORD=dev
```

**2. Coverage Reports**
```bash
ls target/site/jacoco/index.html
# Should exist
```

**3. GitHub Integration**
```bash
ls -la .github/
# Should show: ISSUE_TEMPLATE/, workflows/, CODEOWNERS, dependabot.yml, pull_request_template.md
```

**4. Pre-commit Hook**
```bash
# Test with intentionally bad formatting
echo "public class Test{}" > Test.java
git add Test.java
git commit -m "test"
# Should fail with formatting error
rm Test.java
```

**5. CI/CD Pipeline**
- Go to: https://github.com/TomShtern/Date_Program/actions
- Verify: Latest workflow run passes all jobs
- Check: Green checkmark on recent commits

---

## Rollback Instructions

If anything goes wrong, here's how to undo changes:

### Rollback Individual Files

```bash
# Undo specific file
git checkout HEAD -- .env.example

# Undo entire .github directory
git checkout HEAD -- .github/

# Undo pom.xml changes
git checkout HEAD -- pom.xml
```

### Rollback Entire Implementation

```bash
# Create backup branch first
git branch backup-before-agent-readiness

# Reset to previous commit
git reset --hard HEAD~8  # If you made 8 commits

# Or reset to specific commit
git log --oneline  # Find commit hash
git reset --hard <commit-hash>
```

### Remove Hooks

```bash
# Remove pre-commit hook
rm .git/hooks/pre-commit

# Verify removal
ls .git/hooks/
```

---

## Troubleshooting

### Issue: JaCoCo Coverage Below Threshold

**Symptom:** `mvn verify` fails with coverage error

**Solution:**
1. Check current coverage:
   ```bash
   mvn test jacoco:report
   open target/site/jacoco/index.html
   ```
2. Temporarily lower threshold in pom.xml:
   ```xml
   <minimum>0.70</minimum>  <!-- Instead of 0.80 -->
   ```
3. Gradually add tests to reach 80%

### Issue: Pre-commit Hook Too Slow

**Symptom:** Commits take 30+ seconds

**Solution:**
1. Skip tests in pre-commit:
   - Edit `.git/hooks/pre-commit`
   - Comment out test section
2. Rely on CI/CD for test validation

### Issue: Dependabot Creates Too Many PRs

**Symptom:** 10+ open Dependabot PRs

**Solution:**
1. Edit `.github/dependabot.yml`
2. Reduce `open-pull-requests-limit`:
   ```yaml
   open-pull-requests-limit: 3  # Instead of 5
   ```
3. Change to monthly schedule:
   ```yaml
   schedule:
     interval: "monthly"
   ```

### Issue: CI/CD Workflow Fails

**Symptom:** Red X on GitHub Actions

**Solution:**
1. Check workflow logs:
   ```bash
   gh run list --limit 5
   gh run view <run-id>
   ```
2. Common fixes:
   - Database path issues: Use in-memory H2 for tests
   - Missing dependencies: Clear Maven cache
   - Timeout: Increase timeout or split jobs

---

## Maintenance & Updates

### Monthly Tasks

- [ ] Review Dependabot PRs and merge
- [ ] Check CI/CD performance (build times)
- [ ] Update pre-commit hook if needed
- [ ] Review coverage trends

### Quarterly Tasks

- [ ] Audit CODEOWNERS for accuracy
- [ ] Review issue templates for relevance
- [ ] Update PR template checklist
- [ ] Increase coverage thresholds gradually

### As Needed

- [ ] Add new issue templates for new bug types
- [ ] Update CI/CD workflow for new tools
- [ ] Adjust Dependabot schedule based on volume

---

## Success Metrics

### Quantitative Metrics

| Metric                | Before  | Target | Measurement            |
|-----------------------|---------|--------|------------------------|
| Agent Readiness Level | 2       | 3      | Agent readiness report |
| Level 2 Completion    | 33%     | 81%    | Criteria passed        |
| Overall Readiness     | 37%     | 56%+   | Total score            |
| CI/CD Build Time      | N/A     | <5 min | GitHub Actions         |
| Coverage              | Unknown | 80%+   | JaCoCo report          |
| Dependabot PRs/week   | 0       | 1-3    | GitHub insights        |

### Qualitative Metrics

- ✅ AI agents can self-serve environment setup
- ✅ AI agents get instant feedback on code quality
- ✅ AI agents create properly formatted issues/PRs
- ✅ Dependencies stay up-to-date automatically
- ✅ Code quality standards enforced consistently
- ✅ Test coverage prevents quality regression

---

## Next Steps After Level 3

Once you reach Level 3, consider these Level 4 optimizations:

1. **Fast CI Feedback** - Optimize build times under 3 minutes
2. **Deployment Frequency** - Set up automated releases
3. **Flaky Test Detection** - Track and fix unreliable tests
4. **Code Quality Metrics** - Integrate SonarQube
5. **Test Performance Tracking** - Monitor test execution times

---

## Conclusion

This plan provides a **clear, actionable path** from Level 2 (33%) to Level 3 (81%) agent readiness. The focus is on **practical infrastructure** that directly improves AI agent performance without requiring complex external services.

**Key Benefits:**
- ✅ **Automated feedback loops** - Agents learn faster
- ✅ **Quality guardrails** - Agents can't break things
- ✅ **Standardized outputs** - Agents produce consistent work
- ✅ **Self-service setup** - Agents don't need human help

**Time Investment:**
- Phase 1: 1-2 hours (highest ROI)
- Phase 2: 5 minutes (final push to Level 3)
- Phase 3: Optional enhancements

**Expected Outcome:**
- Level 3 achieved ✅
- Foundation for Level 4+ improvements
- Significantly better AI agent performance

---

**Ready to begin?** Start with Phase 1, Item 1 (`.env.example`) and work through sequentially. Each item provides immediate value even if you don't complete the entire plan.

**Questions or issues?** Refer to the Troubleshooting section or consult the [Agent Readiness Documentation](https://docs.factory.ai/factory-docs/agent-readiness).

---

**Last Updated:** 2026-01-10
**Plan Version:** 1.0
**Repository:** https://github.com/TomShtern/Date_Program
