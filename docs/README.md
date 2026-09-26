# Documentation index

This page separates current entry points from operational references and historical working material. Source code and build configuration remain authoritative; dated audits, plans, and handoffs are context, not a current status report.

## Start here

- [Repository overview and local commands](../README.md)
- [CI and PostgreSQL operations](operations/ci-and-postgresql.md)
- [PostgreSQL PowerShell guide](operations/postgresql-powershell.md)
- [LAN backend startup](operations/lan-backend-startup.md)
- [REST API specification](API-SPECIFICATION.md) — **legacy/unverified**. Its error response and password minimum claims are known to differ from current implementation/configuration. Do not use as an integration contract until reconciled against source.

## Flutter integration

The Android Flutter client is a separate project. The following backend handoffs are retained as history and may describe superseded authentication, endpoint, or setup behavior:

- [Flutter handoff and agent guide](archive/flutter-history/FLUTTER_PROJECT_HANDOFF.md)
- [Flutter frontend agent guide](archive/flutter-history/FLUTTER_FRONTEND_AGENT_GUIDE.md)
- [Phone-alpha API requirements](archive/flutter-history/2026-04-30-phone-alpha-backend-api-requirements.md)
- [Phone-alpha contract audit](archive/flutter-history/2026-04-30-phone-alpha-backend-contract-audit.md)
- [Phone-alpha implementation report](archive/flutter-history/2026-05-01-phone-alpha-backend-implementation-report.md)
- [Path to first alpha](archive/flutter-history/2026-04-30-path-to-first-alpha.md)

## Contributor guidance

- [Agent workflow and verification](../AGENTS.md)
- [Repository map and gotchas](../CLAUDE.md)
- [Copilot repository instructions](../.github/copilot-instructions.md)
- [Gemini guide](../GEMINI.md) and [Qwen guide](../QWEN.md) are model-specific working guidance, not current product documentation.

## Historical material

- [Planning history](archive/planning-history/) — old roadmaps, implementation plans, and review-plan sets.
- [Audit history](archive/audits/) — dated and legacy audit material; findings are not asserted as current.
- [Implementation history](archive/implementation-history/) and [UI overhaul history](archive/ui-overhaul/).
- [Archived reports](archive/reports/), [issues](archive/issues/), [CV drafts](archive/cv/), and [prompts](archive/prompts/).
- [Existing plans](plans/), [reports](reports/), [suggestions](suggestions/), and [engineering/architecture documents](engineering%20and%20architecture%20docs/) remain categorized in their existing folders; check their dates and source references before relying on them.
- [Superpowers specs](superpowers/) are design/planning artifacts, not claims about shipped behavior.

## Internal and snapshot material

`internal/` describes the intended audience only. It is part of this public repository and is visible to anyone with repository access; it is not private storage.

- [AI context snapshots](internal/ai-context/) contain dated architecture, dependency, and source-of-truth notes. Verify every claim against the current source before use.
- [CV/GitHub readiness review](internal/reviews/CV_GITHUB_READINESS_PLAN.md) is a working review document.
- [Backend/store release audit](internal/audits/BACKEND_STORE_RELEASE_AUDIT.md) is a dated review, not a current release certification.
- [Audit prompt](internal/prompts/audit-prompt.md) is a reusable working prompt, not project documentation.

## Repository-level files

Build/configuration files, environment templates, scripts, and the PostgreSQL schema snapshot remain at the repository root because they are used by tooling or are repository-level artifacts. Local runtime state, logs, credentials, and databases do not belong in a public commit. A folder name such as `internal/` does not make committed content private.
