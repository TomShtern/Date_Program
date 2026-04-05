package datingapp.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/** Local runtime environment lookup with optional project `.env` fallback. */
public final class RuntimeEnvironment {

    private static final Path DEFAULT_DOT_ENV_PATH = Path.of(".env");
    private static final AtomicReference<Map<String, String>> DEFAULT_DOT_ENV_CACHE = new AtomicReference<>();

    private RuntimeEnvironment() {}

    public static String getEnv(String envKey) {
        return getEnv(envKey, System::getenv, DEFAULT_DOT_ENV_PATH);
    }

    static String getEnv(String envKey, UnaryOperator<String> envLookup, Path dotEnvPath) {
        String value = envLookup.apply(envKey);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return loadDotEnv(dotEnvPath).get(envKey);
    }

    public static String lookup(String propertyKey, String envKey) {
        return lookup(propertyKey, envKey, System::getProperty, System::getenv, DEFAULT_DOT_ENV_PATH);
    }

    static String lookup(
            String propertyKey,
            String envKey,
            UnaryOperator<String> propertyLookup,
            UnaryOperator<String> envLookup,
            Path dotEnvPath) {
        String propertyValue = propertyLookup.apply(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        return getEnv(envKey, envLookup, dotEnvPath);
    }

    static Map<String, String> parseDotEnvLines(List<String> lines) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }

            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }

            int equalsIndex = line.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }

            String key = line.substring(0, equalsIndex).trim();
            String value = line.substring(equalsIndex + 1).trim();
            values.put(key, stripMatchingQuotes(value));
        }
        return Map.copyOf(values);
    }

    static void clearCacheForTests() {
        DEFAULT_DOT_ENV_CACHE.set(null);
    }

    private static Map<String, String> loadDotEnv(Path dotEnvPath) {
        if (DEFAULT_DOT_ENV_PATH.equals(dotEnvPath)) {
            Map<String, String> cached = DEFAULT_DOT_ENV_CACHE.get();
            if (cached != null) {
                return cached;
            }

            Map<String, String> parsed = readDotEnv(dotEnvPath);
            DEFAULT_DOT_ENV_CACHE.compareAndSet(null, parsed);
            return DEFAULT_DOT_ENV_CACHE.get();
        }

        return readDotEnv(dotEnvPath);
    }

    private static Map<String, String> readDotEnv(Path dotEnvPath) {
        if (!Files.exists(dotEnvPath)) {
            return Map.of();
        }

        try {
            return parseDotEnvLines(Files.readAllLines(dotEnvPath));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read .env file: " + dotEnvPath, exception);
        }
    }

    private static String stripMatchingQuotes(String value) {
        if (value.length() >= 2) {
            if (value.startsWith("\"") && value.endsWith("\"")) {
                return value.substring(1, value.length() - 1);
            }
            if (value.startsWith("'") && value.endsWith("'")) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
