# Consolidated Suggestions & Enhancements — February 6, 2026

**Sources:** Kimmy 2.5, Grok, Opus 4.6, GPT-5 Mini, Raptor Mini, GPT-4.1
**Scope:** Feature suggestions, improvements, missing components, architectural recommendations

---

## Table of Contents

1. [Missing Components & Infrastructure](#1-missing-components--infrastructure)
2. [Security & Authentication](#2-security--authentication)
3. [Performance & Optimization](#3-performance--optimization)
4. [Code Quality & Architecture Improvements](#4-code-quality--architecture-improvements)
5. [Communication & Messaging Features](#5-communication--messaging-features)
6. [AI & Machine Learning](#6-ai--machine-learning)
7. [Privacy & Safety Features](#7-privacy--safety-features)
8. [Social & Community Features](#8-social--community-features)
9. [Gamification & Monetization](#9-gamification--monetization)
10. [UI/UX Enhancements](#10-uiux-enhancements)
11. [Data, Compliance & Observability](#11-data-compliance--observability)
12. [Testing & DevOps](#12-testing--devops)
13. [Architectural Evolution](#13-architectural-evolution)
14. [Implementation Roadmap](#14-implementation-roadmap)

---

## 1. Missing Components & Infrastructure

### 1.1 Authentication System
- Password hashing (Argon2/bcrypt), session tokens/JWT, secure logout
- Forgot-password + email verification workflows
- OAuth/social login (Google, Apple) for easier onboarding
- **Priority:** CRITICAL — blocks production deployment

### 1.2 Push Notification System
- Firebase Cloud Messaging (FCM) for Android
- Apple Push Notification Service (APNS) for iOS
- WebPush for browser notifications
- Notify on new likes, matches, messages
- **Priority:** HIGH

### 1.3 Email & SMS Services
- SendGrid/AWS SES for email (welcome, password reset, match alerts)
- Twilio for SMS verification and notifications
- Email template system + unsubscribe management
- International number support
- **Priority:** HIGH

### 1.4 Content Delivery Network (CDN)
- AWS S3 + CloudFront or CloudFlare for photo serving
- Image optimization pipeline (resize, compress, thumbnails)
- Signed URLs for private photos
- **Priority:** HIGH

### 1.5 Real-Time Infrastructure
- WebSocket server (for typing indicators, presence, instant messages)
- Redis Pub/Sub for horizontal scaling
- Online/offline presence detection
- **Priority:** MEDIUM

### 1.6 Background Job Processing
- Queue system for async tasks (Redis + Bull, RabbitMQ/SQS, or Quartz)
- Jobs for: email sending, stats computation, photo processing, standouts recompute
- Retry logic and dead letter queues
- **Priority:** MEDIUM

### 1.7 Search Infrastructure
- Elasticsearch integration for full-text search on bios
- Faceted search (filter by multiple criteria simultaneously)
- **Priority:** MEDIUM

### 1.8 API Gateway
- Centralized API management (Kong or AWS API Gateway)
- Request routing, rate limiting, authentication
- API versioning strategy
- **Priority:** LOW (until multi-client)

### 1.9 Feature Flags System
- LaunchDarkly, Unleash, or ff4j for gradual rollout
- User segmentation for A/B testing
- Kill switches for emergency rollback
- **Priority:** MEDIUM

---

## 2. Security & Authentication

### 2.1 Input Validation & Sanitization
- Centralize validation with Bean Validation (Jakarta Validation)
- HTML escape all user-generated content (bios, messages, names)
- Content Security Policy headers
- Request size limits on all endpoints
- **Priority:** HIGH

### 2.2 Rate Limiting & Abuse Protection
- Per-IP and per-user rate limiting for:
  - Login attempts (prevent brute force)
  - Message sending (prevent spam)
  - Profile views (prevent stalking)
  - Like/pass actions (prevent bots)
- Token bucket algorithm implementation
- CAPTCHA for suspicious registrations
- **Priority:** HIGH

### 2.3 Session Security
- JWT with expiration and refresh token rotation
- HTTPS enforcement (TLS 1.3, HSTS headers)
- CSRF tokens for state-changing operations
- Session timeout and automatic logout
- **Priority:** HIGH

### 2.4 Data Protection
- Sanitize all logs — never log PII, passwords, or tokens
- Encrypt sensitive data at rest
- Structured logging with redaction levels
- **Priority:** MEDIUM

### 2.5 Bot & Spam Detection
- ML-based behavioral analysis for bot detection
- Phone/email verification requirements
- Rate limiting on profile creation
- **Priority:** MEDIUM

---

## 3. Performance & Optimization

### 3.1 Database Connection Pooling
- Adopt HikariCP for connection management
- Switch from `DriverManager.getConnection()` to `DataSource` pattern
- Configurable pool size, connection timeouts
- **Expected Improvement:** 10-100x throughput under load

### 3.2 Caching Layer
- Caffeine (local) or Redis (distributed) for:
  - User profiles (short TTL)
  - Daily picks (daily TTL)
  - Statistics (hourly TTL)
  - Configuration (indefinite TTL)
  - Match quality scores
  - CandidateFinder results
- **Expected Improvement:** >80% cache hit rate for hot data

### 3.3 Database Query Optimization
- Batch user loading (`UserStorage.findByIds(Set<UUID>)`) to eliminate N+1 patterns
- `INSERT OR IGNORE` for conversation creation instead of check-then-create
- Cursor-based pagination for large result sets
- Incremental stats calculation (running counters) instead of full recalc
- Move spatial filtering to DB layer (bounding box or H2GIS)
- **Expected Improvement:** 60-80% reduction in DB queries

### 3.4 Image Cache Improvements
- True LRU eviction policy with configurable max size
- Time-based cache expiration
- Use `SoftReference` for cache values to allow GC under memory pressure
- Metrics on cache hit rate
- **Expected Improvement:** 50% reduction in memory for image-heavy sessions

### 3.5 Achievement Optimization
- Event-driven achievements (trigger on match/like events) instead of scanning all achievements every time
- **Expected Improvement:** Eliminate unnecessary DB queries per action

### 3.6 Photo Processing Pipeline
- Validate image type and content
- Server-side resize to multiple sizes (thumbnail, medium, full)
- Content-hash deduplication
- Progressive loading in UI
- **Priority:** MEDIUM

---

## 4. Code Quality & Architecture Improvements

### 4.1 Configuration Centralization
- Audit all magic numbers and move to `AppConfig`
- Files needing updates: `AchievementService`, `UiServices`, `Toast`, `MessagingService`, `ProfileViewModel`, `PerformanceMonitor`
- Expose all weights (including standouts) via `AppConfig`

### 4.2 Error Message Standardization
- Create centralized error message constants
- Consistent format: `"❌ {action} failed: {reason}"`
- Structured error codes for API responses (machine-readable)

### 4.3 Consistent Clock Injection
- Inject `Clock` across all services for testability
- Replace all `Instant.now()` and `LocalDate.now()` direct calls
- Enables deterministic testing and timezone consistency

### 4.4 `EnumSet` Safety Utility
- Create `EnumSetUtil.safeCopy(Collection<E>, Class<E>)` helper
- Replace all ad-hoc null/empty guards across codebase
- Add unit tests for empty handling

### 4.5 Database Migration Tooling
- Adopt Flyway (or Liquibase) for versioned migrations
- Extract DDL from Java code into `.sql` files
- Add rollback plans and schema checksums to detect drift

### 4.6 Unified Logging
- Switch `DatabaseManager` from `java.util.logging` to `slf4j`
- Add structured logging with request/session IDs
- Add slow-query logging and connection pool metrics

### 4.7 Soft Deletes
- Add `deleted_at` columns for soft deletes
- Purge after configurable retention (e.g., 90 days)
- Enables data recovery and analytics retention

### 4.8 Implement Proper Pagination
- Add cursor-based pagination to all list operations
- Applies to: messages, matches, conversations, candidate discovery, CLI lists

---

## 5. Communication & Messaging Features

### 5.1 Typing Indicators
- Real-time "User is typing..." in chat
- WebSocket-based for low latency

### 5.2 Read Receipts
- "Delivered" / "Seen" status on messages
- Optional per-user privacy setting

### 5.3 Message Reactions
- Emoji reactions (👍❤️😂) on individual messages
- Quick response without composing a message

### 5.4 Voice Messages
- Record and send audio clips
- Playback controls and waveform visualization

### 5.5 Video Chat
- In-app video calling between matched users (WebRTC or Twilio)
- Pre-date chemistry check and safety

### 5.6 Message Editing & Deletion
- Edit sent messages (with "edited" indicator)
- "Unsend" / soft-delete messages
- Disappearing messages option (Snapchat-style)

### 5.7 Message Scheduling
- Schedule messages for later delivery

### 5.8 Message Filters
- Auto-filter messages containing offensive keywords/patterns
- Reduces harassment

### 5.9 Ghost Timer
- Gentle nudge notification if a conversation goes inactive for 48+ hours
- Reduces ghosting and re-engages users

---

## 6. AI & Machine Learning

### 6.1 AI-Powered Profile Enhancement
- ML suggestions for better photos and bio optimization
- Profile review coach with completion guidance

### 6.2 Smart Match Predictions
- Collaborative filtering for better recommendations
- Behavioral signals: profile view duration, message response rates
- Feedback loop — learn from matches that result in dates

### 6.3 Automated Content Moderation
- AI detection of inappropriate content/photos
- Toxicity detection in messages
- Face detection for photo auto-cropping

### 6.4 Personality Insights
- AI analysis of user behavior for compatibility matching
- ELO/TrueSkill rating for user attractiveness scoring

### 6.5 Smart Conversation Starters
- AI-generated ice breaker suggestions based on shared interests
- Context-aware prompts on new matches

### 6.6 Date Planning Assistant
- AI-powered date suggestions based on shared interests, location, and budget

---

## 7. Privacy & Safety Features

### 7.1 Advanced Privacy Controls
- Granular visibility settings per profile section
- Location privacy with fuzzing/hide modes
- Incognito mode — browse without being seen

### 7.2 Photo Verification
- Selfie-based verification comparing against profile photos
- AI-powered photo authenticity checking
- "Verified" badge system with staged verification levels

### 7.3 Location Spoofing Detection
- Prevent fake location usage

### 7.4 Emergency Contact Integration
- Quick access to emergency services
- Share location with trusted contacts during first dates
- Date check-in prompts
- Safety checklist before first meetings

### 7.5 Block List Import
- Upload phone numbers/emails to preemptively block exes or known people

### 7.6 Block Reason Tracking
- Record reason when blocking for moderation insights

### 7.7 In-App Safety Center
- Report history and status tracking
- Safety tips and resources

### 7.8 Background Checks
- Optional integration with background check services

---

## 8. Social & Community Features

### 8.1 Group Dating Events
- Virtual speed dating and group activities
- Location-based events feed (game nights, cooking classes)

### 8.2 Friend Referrals
- Invite friends with referral bonuses

### 8.3 Social Media Integration
- Import photos/events from social platforms
- Show mutual connections
- Connect Spotify/Apple Music for listening habits

### 8.4 Community Forums
- User discussion boards by interests/location

### 8.5 Matchmaker Mode
- Let friends view and suggest matches for each other

### 8.6 Group Matching
- Friend groups create joint profiles and match with other groups

### 8.7 Interest-Based Group Chats
- Temporary group chats for users with shared interests in same area

### 8.8 Stories
- 24-hour disappearing stories for engagement

### 8.9 Language Exchange Mode
- Filter specifically for language exchange partners

---

## 9. Gamification & Monetization

### 9.1 Super Like
- Limited daily Super Like with prominent notification to target
- Monetization path for unlimited Super Likes

### 9.2 Profile Boost
- Temporarily prioritize user in candidate results
- Time-limited premium visibility windows

### 9.3 Premium Subscription Tiers
- Basic / Premium / VIP with feature gating
- Unlimited likes, rewind, read receipts, incognito mode

### 9.4 Virtual Gifts System
- Monetization through virtual gifts and boosts

### 9.5 Achievement Collections
- Unlockable profile badges and themes
- Seasonal challenges and holiday-themed prompts

### 9.6 Travel/Passport Mode
- Temporary location change for users traveling to new cities

### 9.7 Premium Undo
- Allow premium users to undo any pass, not just the most recent one

---

## 10. UI/UX Enhancements

### 10.1 Profile Prompts
- Fun prompts ("My simple pleasures...", "I want someone who...")
- Auto-rotate featured sections for A/B testing

### 10.2 Relationship Timeline
- Visual timeline of relationship progression
- Key milestones: match date, first message, etc.

### 10.3 Advanced Search & Filtering
- Save search filters, advanced query builder
- Filter presets, search history, advanced criteria
- Multi-faceted filters (education, lifestyle, values)

### 10.4 Multi-Photo Gallery
- Multiple photo support with reorder, delete, crop
- Video profile uploads (15-second intros)
- Audio/short video intro clips

### 10.5 Profile Visit Analytics
- Show who viewed your profile (with privacy controls)
- Trending interests in your area
- Competition analysis (percentile ranking)

### 10.6 Onboarding Wizard
- Guided flow: photo tips, interests wizard, completion tasks
- Progressive profile building

### 10.7 Date Feedback System
- Post-date optional feedback (not visible to other user)
- ML training data for better recommendations

### 10.8 Compatibility Quiz
- Structured personality/compatibility quiz
- Percentage-based matching (like OkCupid)
- Shared results reveal between matches

### 10.9 Photo Request Feature
- Allow matched users to request additional photos

### 10.10 Calendar Integration
- Share availability and suggest meeting times in-app
- Reduces "when should we meet?" friction

### 10.11 Success Story Tracking
- Prompt long-chatting users to report dates/relationships
- Marketing content and success metrics

### 10.12 Profile Deactivation from UI
- "Pause visibility for X days" with auto-resume
- Currently supported in state machine but no UI flow

### 10.13 Admin Dashboard & Moderation
- Admin interface for reviewing reports
- User suspension/ban workflow
- Bulk moderation actions
- Platform stats visualization

### 10.14 Accessible Error Dialogs & Retry UX
- Better error recovery for network/storage failures
- Retry buttons and helpful error messages

### 10.15 Accessibility
- Full WCAG 2.1 AA compliance
- Screen reader support
- Color blind mode with alternative schemes
- Adjustable text sizes
- Voice navigation

---

## 11. Data, Compliance & Observability

### 11.1 GDPR Compliance
- "Right to be forgotten" endpoint
- Data export functionality (user data download)
- Consent management system
- Automated data retention policies (purge old messages >2 years)
- Secure account deletion pipeline

### 11.2 Monitoring & Metrics
- Micrometer + Prometheus for application metrics
- Grafana dashboards for match rates, active users, swipes/sec
- Critical flow instrumentation: match creation, message sending, candidate filtering
- Health & readiness probes (DB connectivity, disk space, env vars)

### 11.3 Distributed Tracing
- OpenTelemetry for critical flows
- Request/session ID correlation in logs

### 11.4 Audit Logging
- Immutable records for all security events
- Track: login attempts, profile changes, reports, bans, blocks
- Separate audit table for moderation actions

### 11.5 Backup & Disaster Recovery
- Automated daily database backups
- Point-in-time recovery capability
- Cross-region replication
- Regular disaster recovery drills

### 11.6 Database Schema Checksums
- Detect drift between code and DB at startup
- Document contract tests for storage interfaces

---

## 12. Testing & DevOps

### 12.1 Storage Integration Tests
- Dedicated test suite for JDBI/H2 with in-memory instances
- Schema and query regression detection
- FK-aware test data setup

### 12.2 Concurrency & Race Condition Tests
- Tests for concurrent match creation, session locking
- ViewModel thread-safety tests

### 12.3 E2E Tests
- Primary flows: profile completion → liking → match → messaging
- Automated UI smoke tests

### 12.4 CI Pipeline Enhancements
- Gates for spotless/spotbugs/checkstyle/qodana
- Fail on TODO/FIXME comments in source files
- Banned pattern detection (e.g., `getFirst()`)
- Production-like checks for DB migrations and health

### 12.5 Load Testing
- Match creation and message throughput benchmarks
- Candidate filtering performance under load

### 12.6 Contract Tests
- Document and test storage interface contracts
- Ensure TestStorages align with JDBI implementations

---

## 13. Architectural Evolution

### 13.1 Microservices Decomposition (Long-Term)
Current monolith could be split into:
- **User Service** — profiles, authentication
- **Matching Service** — likes, matches, candidates
- **Messaging Service** — chat, notifications
- **Media Service** — photos, videos
- **Analytics Service** — stats, ML

### 13.2 Event-Driven Architecture
Replace synchronous calls with events:
- `LikeCreatedEvent` → triggers match check + achievement check
- `MatchCreatedEvent` → triggers notification + stats update
- `MessageSentEvent` → triggers push notification + read receipt

### 13.3 CQRS Pattern
Separate read and write models:
- Writes → normalized relational database
- Reads → denormalized document store (MongoDB/Elasticsearch)

### 13.4 GraphQL API Layer
Replace REST with GraphQL:
- Clients request exactly what they need
- Single endpoint, strong typing, introspection

### 13.5 Web/Mobile Client Parity
- REST API for web/mobile clients
- React Native or Flutter companion app

---

## 14. Implementation Roadmap

### Phase 1: Security & Stability (2 weeks)
**Priority:** CRITICAL
- [ ] Implement proper authentication (Argon2 + JWT)
- [ ] Add comprehensive input validation and sanitization
- [ ] Basic audit logging for security events
- [ ] Fix N+1 query performance issues (batch user loading)
- [ ] Add rate limiting to prevent abuse
- [ ] Fix critical bugs from audit (C-01 through C-08)

### Phase 2: Core Feature Completion (3 weeks)
**Priority:** HIGH
- [ ] Background threading for all ViewModels
- [ ] Real-time push notifications
- [ ] Admin moderation interface
- [ ] Multi-photo gallery support
- [ ] Image caching with LRU eviction
- [ ] Database connection pooling (HikariCP)
- [ ] Database migration tooling (Flyway)

### Phase 3: Performance & Scale (4 weeks)
**Priority:** MEDIUM
- [ ] Implement caching layer (Caffeine/Redis)
- [ ] Database query optimization and indexing
- [ ] Application monitoring and metrics (Prometheus + Grafana)
- [ ] Structured logging with request tracing
- [ ] Configuration centralization (all magic numbers → AppConfig)
- [ ] Memory leak fixes and automatic cleanup
- [ ] Integration test suite for storage layer

### Phase 4: Advanced Features (6+ weeks)
**Priority:** LOW — Nice-to-have
- [ ] Video calling integration (WebRTC)
- [ ] Advanced search and filtering
- [ ] AI-powered matching improvements
- [ ] Social media integrations
- [ ] Premium subscription system
- [ ] GDPR compliance (data export, deletion)
- [ ] Relationship timeline visualization
- [ ] Mobile companion app

---

*Consolidated from 7 independent audit reports — February 6, 2026*
*Sources: Kimmy 2.5, Grok, Opus 4.6, GPT-5 Mini, Raptor Mini, GPT-4.1*
