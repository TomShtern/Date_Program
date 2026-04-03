package datingapp.app.cli;

import datingapp.app.support.UserPresentationSupport;
import datingapp.app.usecase.matching.MatchingUseCases.MatchQualitySnapshot;
import datingapp.app.usecase.matching.MatchingUseCases.SwipeOutcome;
import datingapp.core.TextUtil;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.i18n.I18n;
import datingapp.core.matching.InterestMatcher;
import datingapp.core.matching.MatchingService.PendingLiker;
import datingapp.core.model.User;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Localized presentation helper for matching-related CLI output. */
final class MatchingCliPresenter {

    String noCandidatesFound() {
        return I18n.text("cli.matching.no_candidates");
    }

    String invalidLikePassQuitChoice() {
        return I18n.text("cli.matching.invalid_choice.like_pass_quit");
    }

    String invalidLikePassSkipChoice() {
        return I18n.text("cli.matching.invalid_choice.like_pass_skip");
    }

    String dailyPickHeader() {
        return I18n.text("cli.matching.daily_pick.title");
    }

    String dailyPickResetNotice() {
        return I18n.text("cli.matching.daily_pick.reset");
    }

    String dailyPickDeferred() {
        return I18n.text("cli.matching.daily_pick.deferred");
    }

    String pressEnterPrompt() {
        return I18n.text("cli.common.press_enter_menu");
    }

    List<String> swipeResultLines(SwipeOutcome result, String candidateName) {
        if (result.matched()) {
            return List.of(
                    "",
                    I18n.text("cli.matching.match.banner"),
                    I18n.text("cli.matching.match.message", candidateName),
                    "");
        }
        if (result.like().direction() == Like.Direction.LIKE) {
            return List.of(I18n.text("cli.matching.like_saved"), "");
        }
        return List.of(I18n.text("cli.matching.pass_saved"), "");
    }

    List<String> dailyLimitReachedLines(int likesUsed, String timeUntilReset) {
        return List.of(
                "",
                CliTextAndInput.SEPARATOR_LINE,
                I18n.text("cli.matching.daily_limit.title"),
                CliTextAndInput.SEPARATOR_LINE,
                "",
                I18n.text("cli.matching.daily_limit.used", likesUsed),
                "",
                I18n.text("cli.matching.daily_limit.reset", timeUntilReset),
                "",
                I18n.text("cli.matching.daily_limit.tips"),
                I18n.text("cli.matching.daily_limit.tip.read"),
                I18n.text("cli.matching.daily_limit.tip.quality"),
                I18n.text("cli.matching.daily_limit.tip.matches"),
                "");
    }

    List<String> dailyPickSwipeResultLines(SwipeOutcome result) {
        if (result.matched()) {
            return List.of("", I18n.text("cli.matching.daily_pick.match.banner"), "");
        }
        if (result.like().direction() == Like.Direction.LIKE) {
            return List.of(I18n.text("cli.matching.daily_pick.like"), "");
        }
        return List.of(I18n.text("cli.matching.daily_pick.pass"), "");
    }

    List<String> standoutInteractionResultLines(String candidateName, boolean matched, boolean isLike) {
        if (matched) {
            return List.of("", I18n.text("cli.matching.standout.match", candidateName), "");
        }
        if (isLike) {
            return List.of(I18n.text("cli.matching.standout.like", candidateName), "");
        }
        return List.of(I18n.text("cli.matching.standout.pass", candidateName), "");
    }

    List<String> candidateProfileLines(
            User candidate, User currentUser, double distance, ZoneId userTimeZone, int sharedInterestsPreviewCount) {
        List<String> lines = new ArrayList<>();
        lines.add(CliTextAndInput.BOX_TOP);
        boolean verified = candidate.isVerified();
        int age = UserPresentationSupport.safeAge(candidate, userTimeZone);
        lines.add("│ 💝 " + candidate.getName() + (verified ? " ✅ Verified" : "") + ", " + age + " years old");
        lines.add("│ 📍 " + String.format(Locale.ROOT, "%.1f", distance) + " km away");
        lines.add(CliTextAndInput.PROFILE_BIO_FORMAT.formatted(
                candidate.getBio() != null ? candidate.getBio() : "(no bio)"));

        InterestMatcher.MatchResult matchResult =
                InterestMatcher.compare(currentUser.getInterests(), candidate.getInterests());
        if (matchResult.hasSharedInterests()) {
            String sharedInterests =
                    InterestMatcher.formatSharedInterests(matchResult.shared(), sharedInterestsPreviewCount);
            lines.add("│ " + getMutualInterestsBadge(matchResult.sharedCount()) + " " + matchResult.sharedCount()
                    + " shared interest" + (matchResult.sharedCount() > 1 ? "s" : "") + ": " + sharedInterests);
        }

        lines.add(CliTextAndInput.BOX_BOTTOM);
        return List.copyOf(lines);
    }

    List<String> matchQualityLines(User otherUser, MatchQualitySnapshot quality, ZoneId userTimeZone) {
        List<String> lines = new ArrayList<>();
        String nameUpper = otherUser.getName().toUpperCase(Locale.ROOT);
        lines.add("");
        lines.add(CliTextAndInput.SEPARATOR_LINE);
        lines.add("         MATCH WITH " + nameUpper);
        lines.add(CliTextAndInput.SEPARATOR_LINE + "\n");

        int age = UserPresentationSupport.safeAge(otherUser, userTimeZone);
        lines.add("  👤 " + otherUser.getName() + ", " + age);
        if (otherUser.getBio() != null) {
            lines.add("  📝 " + otherUser.getBio());
        }
        double distanceKm = quality.distanceKm();
        lines.add(
                distanceKm < 0
                        ? "  📍 Distance unknown"
                        : "  📍 " + String.format(Locale.ROOT, "%.1f", distanceKm) + " km away");

        lines.add("\n" + CliTextAndInput.SECTION_LINE);
        lines.add("  COMPATIBILITY: " + quality.compatibilityScore() + "%  " + quality.getStarDisplay());
        lines.add("  " + quality.getCompatibilityLabel());
        lines.add(CliTextAndInput.SECTION_LINE);

        if (!quality.highlights().isEmpty()) {
            lines.add("\n  ✨ WHY YOU MATCHED");
            quality.highlights().forEach(highlight -> lines.add("  • " + highlight));
        }

        lines.addAll(scoreBreakdownLines(quality));

        if (!quality.lifestyleMatches().isEmpty()) {
            lines.add("\n  💫 LIFESTYLE ALIGNMENT");
            quality.lifestyleMatches().forEach(match -> lines.add("  • " + match));
        }

        lines.add("\n  ⏱️  PACE SYNC: " + quality.paceSyncLevel());
        return List.copyOf(lines);
    }

    List<String> likerCardLines(PendingLiker pending, ZoneId userTimeZone) {
        User user = pending.user();
        String verifiedBadge = user.isVerified() ? " ✅ Verified" : "";
        String likedAgo = TextUtil.formatTimeAgo(pending.likedAt());
        String bio = UserPresentationSupport.fallbackBio(user, "", 47);

        return List.of(
                CliTextAndInput.BOX_TOP,
                "│ 💝 " + user.getName() + ", " + UserPresentationSupport.safeAge(user, userTimeZone) + " years old"
                        + verifiedBadge,
                "│ 🕒 Liked you " + likedAgo,
                "│ 📍 Location: " + user.getLat() + ", " + user.getLon(),
                CliTextAndInput.PROFILE_BIO_FORMAT.formatted(bio),
                CliTextAndInput.BOX_BOTTOM,
                "");
    }

    private List<String> scoreBreakdownLines(MatchQualitySnapshot quality) {
        String distanceBar = TextUtil.renderProgressBar(quality.distanceScore(), 12);
        String ageBar = TextUtil.renderProgressBar(quality.ageScore(), 12);
        String interestBar = TextUtil.renderProgressBar(quality.interestScore(), 12);
        String lifestyleBar = TextUtil.renderProgressBar(quality.lifestyleScore(), 12);
        String paceBar = TextUtil.renderProgressBar(quality.paceScore(), 12);
        String responseBar = TextUtil.renderProgressBar(quality.responseScore(), 12);

        return List.of(
                "\n  📊 SCORE BREAKDOWN",
                CliTextAndInput.SECTION_LINE,
                "  Distance:      " + distanceBar + " " + (int) (quality.distanceScore() * 100) + "%",
                "  Age match:     " + ageBar + " " + (int) (quality.ageScore() * 100) + "%",
                "  Interests:     " + interestBar + " " + (int) (quality.interestScore() * 100) + "%",
                "  Lifestyle:     " + lifestyleBar + " " + (int) (quality.lifestyleScore() * 100) + "%",
                "  Pace/Sync:      " + paceBar + " " + (int) (quality.paceScore() * 100) + "%",
                "  Response:      " + responseBar + " " + (int) (quality.responseScore() * 100) + "%");
    }

    private String getMutualInterestsBadge(int sharedCount) {
        return switch (sharedCount) {
            case 5, 6, 7, 8, 9, 10 -> "🎯🔥";
            case 3, 4 -> "🎯✨";
            case 2 -> "🎯";
            default -> "✨";
        };
    }
}
