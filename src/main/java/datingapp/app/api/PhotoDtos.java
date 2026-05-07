package datingapp.app.api;

import java.util.List;

final class PhotoDtos {
    private PhotoDtos() {}

    /** A single uploaded photo reference returned after upload. */
    static record PhotoRef(String id, String url) {}

    /** Response body returned after a successful photo upload. */
    static record PhotoUploadResponse(
            PhotoRef photo,
            String primaryPhotoUrl,
            List<String> photoUrls,
            List<String> missingProfileFields,
            List<String> missingProfileFieldLabels,
            int requiredProfileFieldCount,
            boolean profileComplete,
            boolean canActivate,
            boolean canBrowse) {
        PhotoUploadResponse {
            photoUrls = photoUrls == null ? List.of() : List.copyOf(photoUrls);
            missingProfileFields = missingProfileFields == null ? List.of() : List.copyOf(missingProfileFields);
            missingProfileFieldLabels =
                    missingProfileFieldLabels == null ? List.of() : List.copyOf(missingProfileFieldLabels);
        }
    }

    /** Response body returned after a photo delete or photo reorder. */
    static record PhotoMutationResponse(
            String primaryPhotoUrl,
            List<String> photoUrls,
            List<String> missingProfileFields,
            List<String> missingProfileFieldLabels,
            int requiredProfileFieldCount,
            boolean profileComplete,
            boolean canActivate,
            boolean canBrowse) {
        PhotoMutationResponse {
            photoUrls = photoUrls == null ? List.of() : List.copyOf(photoUrls);
            missingProfileFields = missingProfileFields == null ? List.of() : List.copyOf(missingProfileFields);
            missingProfileFieldLabels =
                    missingProfileFieldLabels == null ? List.of() : List.copyOf(missingProfileFieldLabels);
        }
    }

    /** Request body for reordering a user's photos. */
    static record PhotoOrderRequest(List<String> photoIds) {
        PhotoOrderRequest {
            photoIds = photoIds == null ? List.of() : List.copyOf(photoIds);
        }
    }

    /** Response body returned when listing a user's photos. */
    static record PhotoListResponse(String primaryUrl, List<PhotoRef> photos) {
        PhotoListResponse {
            photos = photos == null ? List.of() : List.copyOf(photos);
        }
    }
}
