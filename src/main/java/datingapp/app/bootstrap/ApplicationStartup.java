package datingapp.app.bootstrap;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.ServiceRegistry;
import datingapp.storage.DatabaseManager;
import datingapp.storage.StorageFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consolidated application startup and configuration loader entry point.
 *
 * <p>
 * Configuration loading uses Jackson databinding with a mix-in strategy to
 * automatically map a
 * flat JSON object onto {@link AppConfig.Builder} private fields — without
 * placing any Jackson
 * annotations inside the pure {@code core/} module (domain-purity rule
 * preserved).
 *
 * <p>
 * Adding a new config property now requires only:
 * <ol>
 * <li>Adding the field + setter to {@link AppConfig.Builder} with the same
 * camelCase name.
 * <li>Adding the corresponding key to {@code config/app-config.json}.
 * </ol>
 * <strong>No changes to this class are needed for new properties.</strong>
 */
public final class ApplicationStartup {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationStartup.class);

    private static final String CONFIG_FILE = "./config/app-config.json";
    private static final String ENV_PREFIX = "DATING_APP_";

    /**
     * Pre-configured {@link ObjectMapper} for config deserialization.
     *
     * <p>
     * The {@link BuilderMixin} is registered on {@link AppConfig.Builder} to:
     * <ul>
     * <li>Enable direct field access ({@code @JsonAutoDetect}) so Jackson can
     * populate the
     * private fields of the builder without needing {@code setXxx}-prefixed methods
     * or
     * Jackson annotations inside {@code core/}.
     * <li>Silence unknown-property errors
     * ({@code @JsonIgnoreProperties(ignoreUnknown = true)})
     * so legacy or future JSON keys that have no corresponding builder field are
     * skipped.
     * </ul>
     * A {@link ZoneIdDeserializer} is also registered to map a plain timezone
     * string like
     * {@code "America/New_York"} to a {@link ZoneId}.
     */
    private static final ObjectMapper MAPPER = buildObjectMapper();

    private static volatile ServiceRegistry services;
    private static volatile DatabaseManager dbManager;
    private static volatile boolean initialized = false;

    private ApplicationStartup() {}

    // ========================================================================
    // Lifecycle
    // ========================================================================

    public static synchronized ServiceRegistry initialize() {
        return initialize(load());
    }

    public static synchronized ServiceRegistry initialize(AppConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        if (!initialized) {
            dbManager = DatabaseManager.getInstance();
            services = StorageFactory.buildH2(dbManager, config);
            initialized = true;
        }
        return services;
    }

    public static ServiceRegistry getServices() {
        ServiceRegistry current = services;
        if (!initialized || current == null) {
            throw new IllegalStateException("ApplicationStartup.initialize() must be called first");
        }
        return current;
    }

    public static synchronized void shutdown() {
        if (dbManager != null) {
            dbManager.shutdown();
        }
        initialized = false;
        services = null;
        dbManager = null;
    }

    public static synchronized void reset() {
        shutdown();
        AppSession.getInstance().reset();
    }

    // ========================================================================
    // Config loading
    // ========================================================================

    public static AppConfig load() {
        return load(Path.of(CONFIG_FILE));
    }

    public static AppConfig load(Path configPath) {
        AppConfig.Builder builder = AppConfig.builder();

        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                applyJsonConfig(builder, json);
                logInfo("Loaded configuration from: {}", configPath);
            } catch (IOException ex) {
                // File exists but failed to read/parse — this is a configuration error, not
                // ignorable
                throw new IllegalStateException("Config file exists but failed to load: " + configPath, ex);
            }
        } else {
            logInfo("Config file not found at {}, using defaults", configPath);
        }

        applyEnvironmentOverrides(builder);
        return builder.build();
    }

    public static AppConfig fromJson(String json) {
        AppConfig.Builder builder = AppConfig.builder();
        applyJsonConfig(builder, json);
        applyEnvironmentOverrides(builder);
        return builder.build();
    }

    /**
     * Applies JSON configuration onto an existing builder using Jackson
     * databinding.
     *
     * <p>
     * {@link ObjectMapper#readerForUpdating} deserializes only the fields present
     * in the JSON
     * document, leaving every other builder field at its default value. This
     * replaces the previous
     * manual property-by-property mapping and means <em>no code changes are needed
     * here</em> when
     * new configuration properties are added to {@link AppConfig.Builder}.
     *
     * <p>
     * If the JSON is malformed the exception is logged and the builder is left with
     * defaults,
     * preserving the previous lenient-on-parse behavior.
     */
    private static void applyJsonConfig(AppConfig.Builder builder, String json) {
        try {
            MAPPER.readerForUpdating(builder).readValue(json);
        } catch (IOException ex) {
            logWarn("Failed to parse JSON config — using defaults for all unread fields", ex);
        }
    }

    // ========================================================================
    // Environment overrides
    // ========================================================================

    /**
     * Applies environment variable overrides on top of whatever JSON (or defaults)
     * set.
     *
     * <p>
     * Environment variables use SCREAMING_SNAKE_CASE with the {@code DATING_APP_}
     * prefix, so an
     * automated mapping like JSON databinding is not practical here. The explicit
     * list is therefore
     * intentional: only variables that are useful to configure at the deployment
     * level are exposed.
     */
    private static void applyEnvironmentOverrides(AppConfig.Builder builder) {
        applyEnvInt("DAILY_LIKE_LIMIT", builder::dailyLikeLimit);
        applyEnvInt("DAILY_SUPER_LIKE_LIMIT", builder::dailySuperLikeLimit);
        applyEnvInt("DAILY_PASS_LIMIT", builder::dailyPassLimit);
        applyEnvInt("AUTO_BAN_THRESHOLD", builder::autoBanThreshold);
        applyEnvInt("MAX_DISTANCE_KM", builder::maxDistanceKm);
        applyEnvInt("MAX_SWIPES_PER_SESSION", builder::maxSwipesPerSession);
        applyEnvInt("UNDO_WINDOW_SECONDS", builder::undoWindowSeconds);
        applyEnvInt("SESSION_TIMEOUT_MINUTES", builder::sessionTimeoutMinutes);
        applyEnvInt("CLEANUP_RETENTION_DAYS", builder::cleanupRetentionDays);
        applyEnvInt("MIN_AGE", builder::minAge);
        applyEnvInt("MAX_AGE", builder::maxAge);

        String tz = System.getenv(ENV_PREFIX + "USER_TIME_ZONE");
        if (tz != null && !tz.isBlank()) {
            try {
                builder.userTimeZone(ZoneId.of(tz));
            } catch (Exception ex) {
                logWarn("Invalid timezone in env var {}{}: {}", ENV_PREFIX, "USER_TIME_ZONE", tz);
            }
        }
    }

    private static void applyEnvInt(String suffix, java.util.function.IntConsumer setter) {
        String value = System.getenv(ENV_PREFIX + suffix);
        if (value != null && !value.isBlank()) {
            try {
                setter.accept(Integer.parseInt(value));
            } catch (NumberFormatException ex) {
                logWarn("Invalid integer in env var {}: {}", ENV_PREFIX + suffix, value);
            }
        }
    }

    // ========================================================================
    // ObjectMapper factory — mix-ins and custom deserializers live here,
    // not inside core/, to preserve domain purity.
    // ========================================================================

    /**
     * Builds and returns the {@link ObjectMapper} used exclusively for config
     * deserialization.
     *
     * <p>
     * The mapper is configured with two concerns:
     * <ol>
     * <li><strong>Mix-in registration</strong> — {@link BuilderMixin} overlays
     * Jackson
     * annotations onto {@link AppConfig.Builder} without touching the {@code core/}
     * package,
     * enabling direct private-field deserialization and unknown-field tolerance.
     * <li><strong>Custom deserializer</strong> — {@link ZoneIdDeserializer}
     * converts a plain
     * timezone ID string into a {@link ZoneId}.
     * </ol>
     */
    private static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register the field-visibility and unknown-property mix-in for
        // AppConfig.Builder.
        // This is the core of the mix-in strategy: we add Jackson metadata to a class
        // in core/
        // without actually modifying that class.
        mapper.addMixIn(AppConfig.Builder.class, BuilderMixin.class);

        // Register a custom deserializer for ZoneId, which Jackson has no built-in
        // handler for.
        SimpleModule module = new SimpleModule("AppConfigModule");
        module.addDeserializer(ZoneId.class, new ZoneIdDeserializer());
        mapper.registerModule(module);

        return mapper;
    }

    // ========================================================================
    // Private Jackson mix-in (annotations only, no implementation)
    // ========================================================================

    /**
     * Jackson mix-in for {@link AppConfig.Builder}.
     *
     * <p>
     * A mix-in is an abstract class whose Jackson annotations are "merged" onto the
     * target class
     * at runtime, avoiding any {@code com.fasterxml.jackson.*} imports inside
     * {@code core/}.
     *
     * <ul>
     * <li>{@link JsonAutoDetect} — enables visibility of private fields so Jackson
     * can populate
     * them directly. Getters, setters, and creator methods are disabled to avoid
     * interfering
     * with the standard POJO deserialization path; only {@code ANY} field
     * visibility is used.
     * <li>{@link JsonIgnoreProperties} — silently skips unknown JSON keys (e.g.,
     * legacy config
     * entries in {@code app-config.json} that no longer correspond to builder
     * fields).
     * </ul>
     */
    @JsonAutoDetect(
            fieldVisibility = Visibility.ANY,
            getterVisibility = Visibility.NONE,
            isGetterVisibility = Visibility.NONE,
            setterVisibility = Visibility.NONE,
            creatorVisibility = Visibility.NONE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private interface BuilderMixin {}

    // ========================================================================
    // Private custom deserializer for ZoneId
    // ========================================================================

    /**
     * Deserializes a JSON string value into a {@link ZoneId}.
     *
     * <p>
     * Jackson does not ship with a built-in deserializer for {@link ZoneId}. This
     * implementation
     * accepts any valid IANA timezone string (e.g., {@code "America/New_York"},
     * {@code "UTC"}).
     * If the string is invalid, {@link ZoneId#of} throws a descriptive exception
     * that propagates
     * as a Jackson deserialization failure.
     */
    private static final class ZoneIdDeserializer extends StdDeserializer<ZoneId> {

        ZoneIdDeserializer() {
            super(ZoneId.class);
        }

        @Override
        public ZoneId deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return ZoneId.of(p.getText());
        }
    }

    // ========================================================================
    // Logging helpers — conditional to suppress allocation at disabled log levels
    // ========================================================================

    private static void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    private static void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }
}
