# Consolidated Codebase Audit — Security & Privacy (February 6, 2026)

**Category:** Authentication, authorization, cryptography, privacy, and API hardening
**Sources:** Kimmy 2.5, Grok, Opus 4.6 (core + storage/UI), GPT-5 Mini, Raptor Mini, GPT-4.1
**Scope:** Full codebase — ~119 production Java files (~19K LOC), 37 test files
**Total Unique Findings:** 75+
<!-- ChangeStamp: 1|2026-02-06 17:29:51|agent:codex|scope:audit-group-security|Regroup audit by problem type (security/privacy)|c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/Audit and Suggestions/AUDIT_PROBLEMS_SECURITY.md -->

---

## Issues

### H-09: Verification Code Uses `java.util.Random` — Not Cryptographically Secure

**File:** `core/TrustSafetyService.java`
**Source:** Grok

```java
private final Random random; // Not SecureRandom
```

`java.util.Random` is predictable and should not be used for security tokens.

**Fix:** Use `SecureRandom`.

---

### H-18: Hardcoded Password in Development

**File:** `storage/DatabaseManager.java`
**Source:** Grok

```java
private static final String DEFAULT_DEV_PASSWORD = "dev";
```

**Fix:** Require password from environment variables; fail fast if not set.

---

### M-25: Logs May Include PII

User names/IDs appear in log messages — privacy risk for production.

---

### API-01: No Authentication or Authorization 🔴

The REST API has zero authentication. Any caller can read any user's profile, like/pass as any user, read any conversation's messages, or send messages as any user.

**Impact:** Complete impersonation, data theft, manipulation.

---

### API-02: `MessagingRoutes.getMessages()` Bypasses Authorization

Calls `messagingStorage.getMessages()` directly instead of `MessagingService.getMessages()` which performs membership checks. Anyone who knows a conversation ID can read all messages.

---

### API-03: No Rate Limiting on Any Endpoint

An attacker could like every user in seconds, send thousands of messages per second, or scrape all profiles.

---

### API-04: `MatchRoutes` Bypasses Daily Limits

`likeUser()` creates a Like via `matchingService.recordLike()` without checking `dailyService.canLike()`. Only the CLI/UI processSwipe path checks daily limits.

---

### API-05: `MatchRoutes.from()` Sets `otherUserName` to "Unknown" Always

The static `from()` method hardcodes "Unknown". The instance `toSummary()` correctly looks up the name, but `likeUser()` uses the static method.

---

### API-06: No Input Sanitization

User-supplied content stored as-is with no HTML/script sanitization. XSS risk if rendered in a web frontend.

---

### API-07: No HTTPS Enforcement

Database credentials could be sent over plaintext if proxy not configured.

---

### API-08: Weak Session Management

Sessions never expire. No TTL on `AppSession`.

**Fix:** Implement JWT with expiration; refresh token rotation.

---

### API-09: No CSRF Protection

State-changing operations have no CSRF token protection.

---

### API-10: Insecure Direct Object Reference

Users may access others' conversations by guessing IDs.

---

### API-11: Sensitive Data Logging

User data may be logged in error messages without redaction.

---

## 6. UI/JavaFX Architecture Issues

---

*Consolidated from 7 independent audit reports — February 6, 2026*
*Auditors: Kimmy 2.5, Grok, Opus 4.6, GPT-5 Mini, Raptor Mini, GPT-4.1*

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
1|2026-02-06 17:29:51|agent:codex|audit-group-security|Regroup audit by problem type (security/privacy)|c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/Audit and Suggestions/AUDIT_PROBLEMS_SECURITY.md
---AGENT-LOG-END---
