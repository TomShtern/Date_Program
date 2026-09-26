# Storage Module Overview

> Persistence layer for the Dating App - JDBI-based database access.

## Package Purpose

The `datingapp.storage` package implements all storage interfaces defined in `datingapp.core.storage`. It provides H2 database persistence using JDBI declarative SQL.

## Key Design Principles

1. **Declarative SQL** - Use JDBI annotations (`@SqlQuery`, `@SqlUpdate`, `@RegisterRowMapper`)
2. **Inlined Mappers** - Row mappers are inner classes within JDBI interfaces
3. **Error Wrapping** - Wrap `SQLException` in `StorageException`
4. **Schema in Code** - `DatabaseManager.initSchema()` creates tables with `IF NOT EXISTS`

## Module Structure

```
storage/
├── DatabaseManager.java          # H2 connection + schema initialization
├── StorageException.java         # Checked exception wrapper
├── jdbi/                         # JDBI interface implementations
│   ├── JdbiBlockStorage.java
│   ├── JdbiConversationStorage.java
│   ├── JdbiDailyPickStorage.java
│   ├── JdbiFriendRequestStorage.java
│   ├── JdbiLikeStorage.java
│   ├── JdbiMatchStorage.java
│   ├── JdbiMessageStorage.java
│   ├── JdbiNotificationStorage.java
│   ├── JdbiPlatformStatsStorage.java
│   ├── JdbiProfileNoteStorage.java
│   ├── JdbiProfileViewStorage.java
│   ├── JdbiReportStorage.java
│   ├── JdbiSwipeSessionStorage.java
│   ├── JdbiUserAchievementStorage.java
│   ├── JdbiUserStatsStorage.java
│   ├── JdbiUserStorage.java
│   ├── JdbiUserStorageAdapter.java  # Adapts JdbiUserStorage to UserStorage interface
│   ├── UserBindingHelper.java       # Serializes User fields for SQL binding
│   ├── EnumSetArgumentFactory.java  # JDBI argument factory for EnumSet
│   └── EnumSetColumnMapper.java     # JDBI column mapper for EnumSet
└── mapper/
    └── MapperHelper.java            # Utility methods for null-safe ResultSet reading
```

## JDBI Interface Pattern

All storage implementations follow this pattern:

```java
@RegisterRowMapper(JdbiBlockStorage.Mapper.class)
public interface JdbiBlockStorage extends BlockStorage {

    @SqlQuery("SELECT * FROM blocks WHERE id = :id")
    Block get(@Bind("id") UUID id);

    @SqlUpdate("INSERT INTO blocks (...) VALUES (...)")
    void save(@BindBean Block block);

    // Inlined row mapper
    class Mapper implements RowMapper<Block> {
        @Override
        public Block map(ResultSet rs, StatementContext ctx) throws SQLException {
            // Use MapperHelper for null-safe reading
            return new Block(
                MapperHelper.readUuid(rs, "id"),
                MapperHelper.readInstant(rs, "created_at")
            );
        }
    }
}
```

## MapperHelper Utilities

| Method | Purpose |
|--------|---------|
| `readUuid(rs, column)` | Null-safe UUID reading |
| `readInstant(rs, column)` | TIMESTAMP → Instant conversion |
| `readLocalDate(rs, column)` | DATE → LocalDate conversion |
| `readEnum(rs, column, enumClass)` | VARCHAR → Enum conversion |
| `readInteger(rs, column)` | Null-safe int with wasNull() |
| `readDouble(rs, column)` | Null-safe double with wasNull() |

## Database Schema

Schema is defined in `DatabaseManager.initSchema()`. Key tables:

| Table | Primary Key | Foreign Keys |
|-------|-------------|--------------|
| `users` | `id` (UUID) | - |
| `likes` | `id` (UUID) | `who_likes`, `who_got_liked` → users |
| `matches` | `id` (VARCHAR) | `user_a`, `user_b` → users |
| `blocks` | `id` (UUID) | `blocker_id`, `blocked_id` → users |
| `messages` | `id` (UUID) | `conversation_id`, `sender_id` → users |
| `conversations` | `id` (VARCHAR) | `user_a`, `user_b` → users |

## Wiring (Dependency Injection)

Storage implementations are wired in `ServiceRegistry.Builder`:

```java
public static ServiceRegistry buildH2(DatabaseManager dbManager, AppConfig config) {
    Jdbi jdbi = dbManager.getJdbi();

    BlockStorage blockStorage = jdbi.onDemand(JdbiBlockStorage.class);
    LikeStorage likeStorage = jdbi.onDemand(JdbiLikeStorage.class);
    // ... etc

    return new ServiceRegistry(blockStorage, likeStorage, ...);
}
```
