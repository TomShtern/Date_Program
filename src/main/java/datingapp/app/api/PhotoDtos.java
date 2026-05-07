package datingapp.app.api;

import java.util.List;
import java.util.Objects;

final class PhotoDtos {
    private PhotoDtos() {}

    private static <T> List<T> cleanList(List<T> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream().filter(Objects::nonNull).toList();
    }

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
            photoUrls = cleanList(photoUrls);
            missingProfileFields = cleanList(missingProfileFields);
            missingProfileFieldLabels = cleanList(missingProfileFieldLabels);
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
            photoUrls = cleanList(photoUrls);
            missingProfileFields = cleanList(missingProfileFields);
            missingProfileFieldLabels = cleanList(missingProfileFieldLabels);
        }
    }

    /** Request body for reordering a user's photos. */
    static record PhotoOrderRequest(List<String> photoIds) {
        PhotoOrderRequest {
            photoIds = cleanList(photoIds);
        }
    }

    /** Response body returned when listing a user's photos. */
    static record PhotoListResponse(String primaryUrl, List<PhotoRef> photos) {
        PhotoListResponse {
            photos = cleanList(photos);
        }
    }
}
