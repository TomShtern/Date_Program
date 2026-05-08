package datingapp.app.api;

import java.util.List;

record ProfileCompletionDto(
        List<String> missingProfileFields,
        List<String> missingProfileFieldLabels,
        int requiredProfileFieldCount,
        boolean profileComplete,
        boolean canActivate,
        boolean canBrowse) {

    ProfileCompletionDto {
        missingProfileFields = missingProfileFields == null ? List.of() : List.copyOf(missingProfileFields);
        missingProfileFieldLabels =
                missingProfileFieldLabels == null ? List.of() : List.copyOf(missingProfileFieldLabels);
    }

    static ProfileCompletionDto of(ProfileCompletionView view) {
        if (view == null) {
            return null;
        }
        return new ProfileCompletionDto(
                view.missingProfileFields(),
                view.missingProfileFieldLabels(),
                view.requiredProfileFieldCount(),
                view.profileComplete(),
                view.canActivate(),
                view.canBrowse());
    }
}
