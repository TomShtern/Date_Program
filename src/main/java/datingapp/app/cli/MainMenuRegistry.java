package datingapp.app.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Centralized immutable registry of CLI main-menu options and dispatch metadata. */
public final class MainMenuRegistry {

    public static final String EXIT_KEY = "0";

    public enum DispatchResult {
        CONTINUE,
        EXIT
    }

    @FunctionalInterface
    public interface MenuAction {
        DispatchResult execute();
    }

    public static record MenuRenderContext(int unreadConversationCount) {
        public MenuRenderContext {
            if (unreadConversationCount < 0) {
                throw new IllegalArgumentException("unreadConversationCount cannot be negative");
            }
        }
    }

    public static final class MenuOption {
        private final String key;
        private final Function<MenuRenderContext, String> displayLabelProvider;
        private final boolean requiresLogin;
        private final MenuAction action;

        private MenuOption(
                String key,
                Function<MenuRenderContext, String> displayLabelProvider,
                boolean requiresLogin,
                MenuAction action) {
            this.key = requireNonBlank(key, "key");
            this.displayLabelProvider =
                    Objects.requireNonNull(displayLabelProvider, "displayLabelProvider cannot be null");
            this.requiresLogin = requiresLogin;
            this.action = Objects.requireNonNull(action, "action cannot be null");
        }

        private static String requireNonBlank(String value, String fieldName) {
            Objects.requireNonNull(value, fieldName + " cannot be null");
            if (value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " cannot be blank");
            }
            return value;
        }

        public String key() {
            return key;
        }

        public boolean requiresLogin() {
            return requiresLogin;
        }

        public MenuAction action() {
            return action;
        }

        public String displayLabel(MenuRenderContext context) {
            Objects.requireNonNull(context, "context cannot be null");
            return displayLabelProvider.apply(context);
        }

        public String renderLine(MenuRenderContext context) {
            return "  " + key + ". " + displayLabel(context);
        }
    }

    private final List<MenuOption> orderedOptions;
    private final Map<String, MenuOption> optionsByKey;

    private MainMenuRegistry(List<MenuOption> orderedOptions) {
        Objects.requireNonNull(orderedOptions, "orderedOptions cannot be null");

        Map<String, MenuOption> index = new LinkedHashMap<>();
        for (MenuOption option : orderedOptions) {
            Objects.requireNonNull(option, "menu option cannot be null");
            if (index.putIfAbsent(option.key(), option) != null) {
                throw new IllegalArgumentException("Duplicate menu key: " + option.key());
            }
        }

        this.orderedOptions = List.copyOf(orderedOptions);
        this.optionsByKey = Map.copyOf(index);
    }

    public static MainMenuRegistry createDefault(Map<String, MenuAction> actionsByKey) {
        Objects.requireNonNull(actionsByKey, "actionsByKey cannot be null");

        return new MainMenuRegistry(List.of(
                option("1", ignored -> "Create new user", false, actionsByKey),
                option("2", ignored -> "Select existing user", false, actionsByKey),
                option("3", ignored -> "Complete my profile", true, actionsByKey),
                option("4", ignored -> "Browse candidates", true, actionsByKey),
                option("5", ignored -> "View my matches", true, actionsByKey),
                option("6", ignored -> "🚫 Block a user", true, actionsByKey),
                option("7", ignored -> "⚠️  Report a user", true, actionsByKey),
                option("8", ignored -> "🔓 Manage blocked users", true, actionsByKey),
                option("9", ignored -> "🎯 Set dealbreakers", true, actionsByKey),
                option("10", ignored -> "📊 View my statistics", true, actionsByKey),
                option("11", ignored -> "👤 Preview my profile", true, actionsByKey),
                option("12", ignored -> "🏆 View achievements", true, actionsByKey),
                option("13", ignored -> "📝 My profile notes", true, actionsByKey),
                option("14", ignored -> "📊 Profile completion score", true, actionsByKey),
                option("15", ignored -> "✅ Verify my profile", true, actionsByKey),
                option("16", ignored -> "💌 Who liked me", true, actionsByKey),
                option("17", MainMenuRegistry::conversationLabel, true, actionsByKey),
                option("18", ignored -> "🔔 Notifications", true, actionsByKey),
                option("19", ignored -> "🤝 Friend Requests", true, actionsByKey),
                option("20", ignored -> "🌟 View Standouts", true, actionsByKey),
                option(EXIT_KEY, ignored -> "Exit", false, actionsByKey)));
    }

    private static MenuOption option(
            String key,
            Function<MenuRenderContext, String> displayLabelProvider,
            boolean requiresLogin,
            Map<String, MenuAction> actionsByKey) {
        MenuAction action = Objects.requireNonNull(actionsByKey.get(key), "Missing menu action for key " + key);
        return new MenuOption(key, displayLabelProvider, requiresLogin, action);
    }

    private static String conversationLabel(MenuRenderContext context) {
        int unread = context.unreadConversationCount();
        if (unread <= 0) {
            return "💬 Conversations";
        }
        return "💬 Conversations (" + unread + " new)";
    }

    public List<MenuOption> orderedOptions() {
        return orderedOptions;
    }

    public Optional<MenuOption> findOption(String key) {
        return Optional.ofNullable(optionsByKey.get(key));
    }

    public List<String> renderOptionLines(MenuRenderContext context) {
        Objects.requireNonNull(context, "context cannot be null");
        return orderedOptions.stream().map(option -> option.renderLine(context)).toList();
    }
}
