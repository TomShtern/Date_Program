package datingapp.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages H2 database connections and schema initialization.
 */
public class DatabaseManager {

    private static String jdbcUrl = "jdbc:h2:./data/dating;AUTO_SERVER=TRUE";
    private static final String USER = "sa";
    private static final String PASSWORD = "changeit";

    private static DatabaseManager instance;
    private boolean initialized = false;

    public static void setJdbcUrl(String url) {
        jdbcUrl = url;
    }

    public static void resetInstance() {
        instance = null;
    }

    private DatabaseManager() {
        // Driver loaded automatically by SPI
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Gets a new database connection.
     */
    public Connection getConnection() throws SQLException {
        if (!initialized) {
            initializeSchema();
        }
        return DriverManager.getConnection(jdbcUrl, USER, PASSWORD);
    }

    /**
     * Initializes the database schema.
     */
    private synchronized void initializeSchema() {
        if (initialized) {
            return;
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl, USER, PASSWORD);
                Statement stmt = conn.createStatement()) {

            // Users table
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id UUID PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        bio VARCHAR(500),
                        birth_date DATE,
                        gender VARCHAR(20),
                        interested_in VARCHAR(100),
                        lat DOUBLE,
                        lon DOUBLE,
                        max_distance_km INT DEFAULT 50,
                        min_age INT DEFAULT 18,
                        max_age INT DEFAULT 99,
                        photo_urls VARCHAR(1000),
                        state VARCHAR(20) NOT NULL DEFAULT 'INCOMPLETE',
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        -- Lifestyle fields (Phase 0.5b)
                        smoking VARCHAR(20),
                        drinking VARCHAR(20),
                        wants_kids VARCHAR(20),
                        looking_for VARCHAR(20),
                        education VARCHAR(20),
                        height_cm INT,
                        -- Dealbreaker fields (Phase 0.5b) - CSV storage
                        db_smoking VARCHAR(100),
                        db_drinking VARCHAR(100),
                        db_wants_kids VARCHAR(100),
                        db_looking_for VARCHAR(100),
                        db_education VARCHAR(200),
                        db_min_height_cm INT,
                        db_max_height_cm INT,
                        db_max_age_diff INT
                    )
                    """);

            // Add columns for existing databases (Phase 0.5b migration)
            // These will be ignored if columns already exist
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS smoking VARCHAR(20)");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS drinking VARCHAR(20)");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS wants_kids VARCHAR(20)");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS looking_for VARCHAR(20)");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS education VARCHAR(20)");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS height_cm INT");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_smoking VARCHAR(100)");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_drinking VARCHAR(100)");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_wants_kids VARCHAR(100)");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_looking_for VARCHAR(100)");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_education VARCHAR(200)");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_min_height_cm INT");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_max_height_cm INT");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_max_age_diff INT");
            } catch (SQLException e) {
                // Column already exists, ignore
            }

            // Likes table
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS likes (
                        id UUID PRIMARY KEY,
                        who_likes UUID NOT NULL,
                        who_got_liked UUID NOT NULL,
                        direction VARCHAR(10) NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        CONSTRAINT fk_likes_who_likes FOREIGN KEY (who_likes) REFERENCES users(id),
                        CONSTRAINT fk_likes_who_got_liked FOREIGN KEY (who_got_liked) REFERENCES users(id),
                        CONSTRAINT uk_likes UNIQUE (who_likes, who_got_liked)
                    )
                    """);

            // Matches table
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS matches (
                        id VARCHAR(100) PRIMARY KEY,
                        user_a UUID NOT NULL,
                        user_b UUID NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        CONSTRAINT fk_matches_user_a FOREIGN KEY (user_a) REFERENCES users(id),
                        CONSTRAINT fk_matches_user_b FOREIGN KEY (user_b) REFERENCES users(id),
                        CONSTRAINT uk_matches UNIQUE (user_a, user_b)
                    )
                    """);

            // Swipe sessions table (Phase 0.5b)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS swipe_sessions (
                        id UUID PRIMARY KEY,
                        user_id UUID NOT NULL,
                        started_at TIMESTAMP NOT NULL,
                        last_activity_at TIMESTAMP NOT NULL,
                        ended_at TIMESTAMP,
                        state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                        swipe_count INT NOT NULL DEFAULT 0,
                        like_count INT NOT NULL DEFAULT 0,
                        pass_count INT NOT NULL DEFAULT 0,
                        match_count INT NOT NULL DEFAULT 0,
                        CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id)
                    )
                    """);

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_likes_who_likes ON likes(who_likes)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_matches_user_a ON matches(user_a)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_matches_user_b ON matches(user_b)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON swipe_sessions(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user_active ON swipe_sessions(user_id, state)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_started_at ON swipe_sessions(user_id, started_at)");

            // User stats snapshots table (Phase 0.5b)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS user_stats (
                        id UUID PRIMARY KEY,
                        user_id UUID NOT NULL,
                        computed_at TIMESTAMP NOT NULL,
                        total_swipes_given INT NOT NULL DEFAULT 0,
                        likes_given INT NOT NULL DEFAULT 0,
                        passes_given INT NOT NULL DEFAULT 0,
                        like_ratio DOUBLE NOT NULL DEFAULT 0.0,
                        total_swipes_received INT NOT NULL DEFAULT 0,
                        likes_received INT NOT NULL DEFAULT 0,
                        passes_received INT NOT NULL DEFAULT 0,
                        incoming_like_ratio DOUBLE NOT NULL DEFAULT 0.0,
                        total_matches INT NOT NULL DEFAULT 0,
                        active_matches INT NOT NULL DEFAULT 0,
                        match_rate DOUBLE NOT NULL DEFAULT 0.0,
                        blocks_given INT NOT NULL DEFAULT 0,
                        blocks_received INT NOT NULL DEFAULT 0,
                        reports_given INT NOT NULL DEFAULT 0,
                        reports_received INT NOT NULL DEFAULT 0,
                        reciprocity_score DOUBLE NOT NULL DEFAULT 0.0,
                        selectiveness_score DOUBLE NOT NULL DEFAULT 0.5,
                        attractiveness_score DOUBLE NOT NULL DEFAULT 0.5,
                        CONSTRAINT fk_user_stats_user FOREIGN KEY (user_id) REFERENCES users(id)
                    )
                    """);

            // Platform stats table (Phase 0.5b)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS platform_stats (
                        id UUID PRIMARY KEY,
                        computed_at TIMESTAMP NOT NULL,
                        total_active_users INT NOT NULL DEFAULT 0,
                        avg_likes_received DOUBLE NOT NULL DEFAULT 0.0,
                        avg_likes_given DOUBLE NOT NULL DEFAULT 0.0,
                        avg_match_rate DOUBLE NOT NULL DEFAULT 0.0,
                        avg_like_ratio DOUBLE NOT NULL DEFAULT 0.5
                    )
                    """);

            // Stats indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_stats_user_id ON user_stats(user_id)");
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_user_stats_computed_at ON user_stats(user_id, computed_at DESC)");
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_platform_stats_computed_at ON platform_stats(computed_at DESC)");

            initialized = true;

        } catch (SQLException e) {
            throw new StorageException("Failed to initialize database schema", e);
        }
    }

    /**
     * Shuts down the database gracefully.
     */
    public void shutdown() {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, USER, PASSWORD);
                Statement stmt = conn.createStatement()) {
            stmt.execute("SHUTDOWN");
        } catch (SQLException e) {
            // Ignore shutdown errors
        }
    }
}
