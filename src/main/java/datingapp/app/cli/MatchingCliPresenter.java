package datingapp.app.cli;

import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.i18n.I18n;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import java.util.List;

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

    List<String> swipeResultLines(MatchingService.SwipeResult result, String candidateName) {
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

    List<String> dailyLimitReachedLines(RecommendationService.DailyStatus status, String timeUntilReset) {
        return List.of(
                "",
                CliTextAndInput.SEPARATOR_LINE,
                I18n.text("cli.matching.daily_limit.title"),
                CliTextAndInput.SEPARATOR_LINE,
                "",
                I18n.text("cli.matching.daily_limit.used", status.likesUsed()),
                "",
                I18n.text("cli.matching.daily_limit.reset", timeUntilReset),
                "",
                I18n.text("cli.matching.daily_limit.tips"),
                I18n.text("cli.matching.daily_limit.tip.read"),
                I18n.text("cli.matching.daily_limit.tip.quality"),
                I18n.text("cli.matching.daily_limit.tip.matches"),
                "");
    }

    List<String> dailyPickSwipeResultLines(MatchingService.SwipeResult result, String candidateName) {
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
}
