# DatingApp PostgreSQL Schema Review

> Date: 2026-04-08
> Scope: Live review of the `datingapp` PostgreSQL schema plus the key repo files that explain its intent.
> Change policy: analysis only — no schema or code changes were made.

## Executive summary

The schema largely makes sense for the application domain. It is centered on a single `users` table, with interaction/event tables around it, plus normalized row-per-value tables for multi-valued preferences and profile data. The biggest strengths are:

- clear user-centric foreign-key structure with `ON DELETE CASCADE`
- good use of composite primary keys for pure membership/event tables
- deterministic pair IDs for two-user aggregates (`matches`, `conversations`)
- targeted read indexes, including partial indexes for soft-deleted data paths
- a clean append-only migration model through `MigrationRunner`

The biggest improvement opportunities are not about rethinking the whole model; they are mostly about tightening invariants and clarifying intent:

1. add stronger database-level constraints for categorical values and lifecycle rules
2. clarify where `deleted_at` means “soft-deleted tombstone” versus “row can be recreated later”
3. review the small set of important `*_id` columns that are not protected by foreign keys
4. plan now for time-zone-safe timestamps before any later remote/cloud rollout
5. keep the current model, but treat the wide `users` table as the main future normalization hotspot

## Quick schema map

The live `public` schema currently contains **28 base tables**.

### Core entity and 1:N detail tables

- `users`
- `user_photos`
- `user_interests`
- `user_interested_in`
- `user_db_smoking`
- `user_db_drinking`
- `user_db_wants_kids`
- `user_db_looking_for`
- `user_db_education`

### Interaction / relationship tables

- `likes`
- `matches`
- `conversations`
- `messages`
- `friend_requests`
- `blocks`
- `reports`
- `notifications`
- `profile_notes`
- `profile_views`

### Recommendation / session / undo tables

- `daily_picks`
- `daily_pick_views`
- `standouts`
- `swipe_sessions`
- `undo_states`

### Analytics / achievement tables

- `user_stats`
- `platform_stats`
- `user_achievements`

### Operational table

- `schema_version`

## What already makes sense

### 1. Multi-valued user preferences are normalized properly

The `user_interests`, `user_interested_in`, and `user_db_*` tables use clean composite keys like `(user_id, interest)` or `(user_id, value)`. That is a good fit for multi-select attributes.

Important semantic note: the `user_db_*` tables are not duplicate copies of profile traits. They represent **deal-breaker / acceptable-value preference sets** used for matching, which is a sensible reason for them to exist separately from the scalar profile fields on `users`.

### 2. Deterministic IDs for two-user aggregates are intentional and coherent

`matches.id` and `conversations.id` are `VARCHAR` rather than UUID because they represent canonical pair identifiers, not arbitrary surrogate keys. That is a valid design for two-user aggregates because it guarantees order-independent identity (`A_B` is the same aggregate as `B_A`).

### 3. Composite keys are used well in several places

Tables such as `daily_picks`, `daily_pick_views`, `profile_notes`, `profile_views`, `user_photos`, and the normalized preference tables all use composite primary keys where the natural key is obvious. That keeps those tables simpler and avoids unnecessary surrogate IDs.

### 4. The indexing strategy is already more thoughtful than average

The schema includes both baseline lookup indexes and some useful partial indexes, for example:

- `likes(direction, created_at DESC) WHERE deleted_at IS NULL`
- `conversations(user_a, last_message_at DESC) WHERE deleted_at IS NULL`
- `messages(sender_id, created_at DESC) WHERE deleted_at IS NULL`
- `standouts(seeker_id, interacted_at DESC) WHERE interacted_at IS NOT NULL`

That suggests the schema already reflects real read-path tuning rather than being purely CRUD-shaped.

### 5. Derived/analytical data is separated from transactional tables

`user_stats`, `platform_stats`, and `user_achievements` are separate from the main interaction tables. That separation is healthy and avoids overloading the core transactional rows.

## Important observations and recommendations

### High priority observations

| Priority | Finding                                                           | Why it matters                                                                                                                                                                                                                                                                                                                                                                  | Recommendation                                                                                                                                                                                                                                           |
|----------|-------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| High     | Categorical columns are mostly enforced only in application code  | Many columns use `VARCHAR` without `CHECK` constraints or PostgreSQL enum/domain protection (`users.state`, `users.gender`, `matches.state`, `friend_requests.status`, `likes.direction`, `reports.reason`, `notifications.type`, pace fields, and the `value` columns in normalized preference tables). Invalid values can reach the database even if the app usually behaves. | First audit actual live values, then add staged `CHECK` constraints for the safest columns. This is the highest-value no-behavior-change hardening step if the data is already clean.                                                                    |
| High     | Soft-delete semantics are not uniform and should be made explicit | Several tables have `deleted_at`, but uniqueness is often still enforced across all rows, not only active rows. That means deleted rows act like tombstones. Example pattern: `likes`, `blocks`, `reports`, `profile_notes`, and `conversations`.                                                                                                                               | Decide table-by-table whether soft delete means “historical tombstone” or “re-creatable row.” If tombstoning is intended, document it. If active-only uniqueness is intended, move to partial unique indexes such as `... WHERE deleted_at IS NULL`.     |
| High     | A few important reference columns are unconstrained               | `matches.ended_by` is not protected by a foreign key. `undo_states` stores `user_id`, `like_id`, `who_likes`, `who_got_liked`, and `match_id` with no FK enforcement at all.                                                                                                                                                                                                    | Make an explicit decision per column. For `undo_states`, the denormalized snapshot design is understandable, so documentation may be enough. For `matches.ended_by`, a foreign key to `users(id)` is a strong candidate if lifecycle semantics allow it. |
| High     | All audit timestamps are `timestamp without time zone`            | This is survivable locally, but it becomes risky as soon as the system relies on multiple environments, multiple regions, or cloud-hosted operational tooling.                                                                                                                                                                                                                  | Do not change this immediately if current behavior assumes local/naive timestamps, but treat a future `TIMESTAMPTZ` migration plan as important technical debt.                                                                                          |

### Medium priority observations

| Priority | Finding                                                                                     | Why it matters                                                                                                                                                                                                                                                      | Recommendation                                                                                                                                                                                            |
|----------|---------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Medium   | `users` is the main growth hotspot                                                          | The table currently mixes identity, profile text, location, verification, lifestyle traits, pace preferences, and matching preferences. That is still manageable, but it is the obvious place where future growth will hurt readability and migration safety first. | Keep it as-is for now. If more 1:1 fields are added later, split by concern rather than by technical layer — for example `user_verification`, `user_location`, or `user_match_preferences`.               |
| Medium   | Deterministic pair IDs are stored as `VARCHAR(100)` even though the canonical size is fixed | A canonical pair of UUIDs plus `_` is 73 characters. `friend_requests.pair_key` already uses `VARCHAR(73)`, which exposes the mismatch.                                                                                                                             | Add a length check or tighten `matches.id` and `conversations.id` to match the actual canonical size. This is a clean simplification that preserves behavior.                                             |
| Medium   | `friend_requests` contains PostgreSQL-simplifiable compatibility helpers                    | `pair_key` and `pending_marker` exist to enforce one pending request per pair while preserving history. This works, but it is more complex than a PostgreSQL-only design would need.                                                                                | Keep this while H2 compatibility remains important. If the repo ever becomes PostgreSQL-only, replace the helper-column pattern with a partial unique index on pending rows.                              |
| Medium   | Stats tables would benefit from one more invariant decision                                 | `user_stats` and `platform_stats` look like snapshot/history tables, but there is no uniqueness rule around computation instants.                                                                                                                                   | If duplicate snapshots at the same instant are meaningless, consider unique keys such as `(user_id, computed_at)` for `user_stats` and `(computed_at)` for `platform_stats` after auditing existing data. |
| Medium   | `has_location_set` duplicates information already implied by `lat`/`lon`                    | The boolean can drift from the actual coordinate nullability state unless the application keeps them perfectly synchronized.                                                                                                                                        | If it is still useful, keep it but document it as a cache/derived flag. If it is not measurably helpful, consider eventually deriving location-set state from coordinates alone.                          |

### Low priority observations

| Priority | Finding                                                      | Why it matters                                                                                                                     | Recommendation                                                                                                                                 |
|----------|--------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| Low      | `notifications.data_json` is plain `text`, not `jsonb`       | If the payload is purely opaque, this is fine. If the app ever filters on notification payload fields, `jsonb` is the better type. | Keep as `text` unless querying/filtering inside the payload becomes a real requirement.                                                        |
| Low      | `users.email` and `users.phone` are not uniquely constrained | That may be intentional today, but it matters if those fields later become stable user identities.                                 | If login/identity eventually depends on them, add uniqueness and likely case-insensitive email handling (`citext` or normalized unique index). |

## Special notes on intentional patterns

### `undo_states` looks intentionally denormalized

The schema plus domain/storage code point to `undo_states` being a short-lived self-contained snapshot table for the undo window, not a first-class relational entity. That means the missing foreign keys look more like a deliberate trade-off than an oversight.

My suggestion is not “normalize it immediately.” My suggestion is to **document that choice clearly** and add only the minimum additional constraint(s) that do not break the snapshot workflow.

### The `user_db_*` tables are semantically valid

Despite the awkward naming, these tables make sense. They represent multi-valued **acceptable/dealbreaker** preference sets for matching logic, not duplicated versions of the user’s own smoking/drinking/etc. attributes.

The stronger improvement here is naming clarity, not structural redesign.

### Soft delete currently behaves more like tombstoning in several places

That is not inherently wrong. In fact, it may be exactly what you want for likes, blocks, reports, or notes. The important part is to make the rule explicit so future contributors do not assume that a soft-deleted row can always be recreated cleanly.

## Suggested no-behavior-change improvement order

1. **Audit real data values and add safe categorical `CHECK` constraints**.
2. **Document soft-delete/tombstone semantics table-by-table**.
3. **Review and justify the unconstrained reference columns** (`matches.ended_by`, `undo_states.*`).
4. **Constrain deterministic string IDs more tightly**.
5. **Prepare — but do not rush — a separate `TIMESTAMPTZ` migration strategy**.

## Suggestions to avoid for now

- Do **not** rewrite the schema around PostgreSQL-specific features just because PostgreSQL is now the runtime.
- Do **not** split `users` immediately unless new requirements force it.
- Do **not** convert text payloads to `jsonb` without a concrete query use case.
- Do **not** remove the H2-compatibility-driven patterns unless the repo intentionally retires H2.

## Bottom line

The schema is broadly sensible and already more disciplined than a typical app schema at this stage. The biggest wins are now about **tightening guarantees** and **clarifying invariants**, not inventing a brand-new design.

If I were prioritizing improvements without changing user-visible behavior, I would start with:

1. categorical-value constraints,
2. explicit soft-delete semantics,
3. a decision on unconstrained reference columns,
4. and a future-safe timestamp strategy.
