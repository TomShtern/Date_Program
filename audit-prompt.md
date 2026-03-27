# Workspace Java Code Audit Prompt (Date_Program)

You are auditing this exact repository:
`c:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program`

## Mission
Perform a deep, code-first technical audit of the Java codebase and generate a single report file in the repository root.

Your analysis must focus on real implementation behavior, architecture, and data flow. Do not produce generic advice.

## Non-Negotiable Output
Create exactly one Markdown report file in the repo root using this filename pattern:

`Generated_Report_Generated_By_<MODEL>_<DD.MM.YYYY>.md`

Example:
`Generated_Report_Generated_By_GPT-5.3-Codex-Exhigh_21.02.2026.md`

Rules:
- `<MODEL>` must be your actual model name, sanitized for filename safety.
- Date format must be exactly `DD.MM.YYYY`.
- Report language must be English.

## Source-of-Truth Rules
- The code is the ONLY source of truth.
- Do not use docs, markdown files, audit notes, or previous generated reports as evidence.
- Do not trust comments unless validated by implementation.

## Scope (Strict)
Analyze only Java source files:
- `src/main/java/**`
- `src/test/java/**`

Exclude:
- `docs/**` (including any `.java` files there)
- `target/**`
- logs and generated artifacts
- markdown/text documents
- non-Java resources as evidence (FXML/CSS/config/docs)

## Analysis Expectations
1. Build a mental model of architecture and data flow from code.
2. Trace key execution paths end-to-end (entry points -> services -> storage -> outputs).
3. Prioritize high-signal findings; cap total findings at 30.
4. Prioritize `MAIN` findings over `TEST` findings.
5. Include `TEST` findings when they reveal gaps, fragility, or masking of production defects.

## Optional Commands Policy
- You may run non-mutating commands only when they add insight.
- Prefer PMD signal if running checks: `mvn pmd:check`.
- Do not run commands only to confirm the app starts/runs.

## Required Categories
You must include findings in these four categories:
1. Missing implementations/features/capabilities.
2. Duplication/redundancy/simplification opportunities.
3. Logic/architecture/structure flaws.
4. Clear problems/issues/mistakes.

If a category has no high-confidence finding, explicitly state:
`No high-confidence findings in this category.`

## Per-Finding Required Format
For every finding, include all of the following fields:
- `ID`: unique identifier (e.g., `F-001`)
- `Category`: one of the 4 required categories
- `Severity`: Critical | High | Medium | Low
- `ScopeTag`: `[MAIN]` or `[TEST]`
- `Impact`: 1-5
- `Effort`: 1-5
- `Confidence`: 1-5
- `Location`: exact file path + exact line reference(s)
- `Evidence`: short code excerpt
- `Why this is an issue`: explain root cause and why current behavior/design is problematic
- `Current negative impact`: concrete present-day cost/risk (bugs, performance, maintainability, correctness, security, etc.)
- `Impact of fixing`: concrete expected benefit after remediation
- `Recommended fix direction`: specific, actionable remediation guidance

No finding is valid without exact line-level evidence.

## Report Structure (Mandatory)
Use this exact top-level structure:

1. `# Executive Summary`
- Brief overview of system health and dominant risk themes.
- Total findings count.

2. `# Architecture and Data-Flow Understanding (Code-Derived)`
- Summarize runtime entry points and wiring discovered from Java code.
- Describe major data flows across layers.

3. `# Findings`
- Organize by the 4 required categories.
- Within each category, order by severity then confidence.
- Maximum 30 findings total.

4. `# Prioritized Remediation Roadmap`
- `Phase 1: Quick Wins`
- `Phase 2: Refactors`
- `Phase 3: Strategic Improvements`

5. `# Strategic Options and Alternatives`
For each of the following, provide up to 5 high-quality alternatives.
Each option must include: expected value, rough effort, risk, and dependencies.
- What should the next steps be?
- What should be implemented but is not?
- What features/components are missing?
- What changes would improve/add value?
- Final recommendations not already covered.

6. `# Acceptance Checklist`
Explicitly verify:
- Filename matches required pattern and is in repo root.
- Report includes code-derived architecture/data flow.
- All 4 categories are present.
- Every finding includes strict line-level evidence plus:
  - why it is an issue
  - current negative impact
  - impact of fixing
- Total findings <= 30.
- Forward section includes phased roadmap + 5 question groups (<= 5 options each).

## Quality Bar
- Be concrete and technical, not generic.
- Favor fewer high-confidence findings over noisy speculation.
- Tie claims directly to observed code paths.
- Avoid duplicate findings; merge overlapping issues.

## Delivery
When complete, ensure the report file is saved in the repository root and contains all required sections and fields.
