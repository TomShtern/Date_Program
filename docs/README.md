# Documentation index

The source in `src/main/java`, `src/test/java` and `pom.xml` is the reference.
If a page here disagrees with it, the page is wrong.

## Start here

- [Project overview and run commands](../README.md)
- [Architecture](architecture/architecture.md), with module notes for
  [core](architecture/modules/core-module-overview.md) and
  [storage](architecture/modules/storage-module-overview.md)
- [Known limitations](known-limitations.md)

## Running and operating

- [CI and PostgreSQL](guides/ci-and-postgresql.md)
- [PostgreSQL PowerShell guide](guides/postgresql-powershell.md)
- [LAN backend startup](guides/lan-backend-startup.md)
- [Public HTTPS backend via Tailscale Funnel](guides/public-funnel-runbook.md)
- [Runtime configuration](reference/runtime-configuration.md)

## API

- [REST API specification](api/API-SPECIFICATION.md) covers the auth and photo
  routes. The full route list is in `RestApiServer`.

## Contributor files

- [AGENTS.md](../AGENTS.md) has the execution and verification workflow for
  coding agents.
- [CLAUDE.md](../CLAUDE.md) has the repo map and the gotchas that have caused bugs.
- [.github/copilot-instructions.md](../.github/copilot-instructions.md) has the
  always-on rules for Copilot.
