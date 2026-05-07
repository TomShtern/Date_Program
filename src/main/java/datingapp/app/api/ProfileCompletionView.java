package datingapp.app.api;

import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.workflow.ProfileActivationPolicy;
import java.util.List;
import java.util.Objects;

record ProfileCompletionView(
        List<String> missingProfileFields,
        List<String> missingProfileFieldLabels,
        int requiredProfileFieldCount,
        boolean profileComplete,
        boolean canActivate,
        boolean canBrowse) {

    ProfileCompletionView {
        missingProfileFields = cleanList(missingProfileFields);
        missingProfileFieldLabels = cleanList(missingProfileFieldLabels);
    }

    private static <T> List<T> cleanList(List<T> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream().filter(Objects::nonNull).toList();
    }

    static ProfileCompletionView from(User user, ProfileActivationPolicy activationPolicy) {
        boolean profileComplete = user.isComplete();
        boolean canActivate = activationPolicy.canActivate(user).isAllowed();
        boolean canBrowse = user.getState() == UserState.ACTIVE && profileComplete;
        return new ProfileCompletionView(
                user.getMissingProfileFields(),
                user.getMissingProfileFieldDisplayNames(),
                user.getRequiredProfileFieldCount(),
                profileComplete,
                canActivate,
                canBrowse);
    }
}
