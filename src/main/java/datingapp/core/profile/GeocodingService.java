package datingapp.core.profile;

import datingapp.core.model.LocationModels.Precision;
import datingapp.core.model.LocationModels.ResolvedLocation;
import java.util.List;
import java.util.Objects;

/** Country-specific geocoding abstraction for the profile location flow. */
public interface GeocodingService {

    List<GeocodingResult> search(String query, int maxResults);

    record GeocodingResult(String displayName, double latitude, double longitude, Precision precision) {
        public GeocodingResult {
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("displayName cannot be blank");
            }
            if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
                throw new IllegalArgumentException("latitude must be between -90 and 90");
            }
            if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
                throw new IllegalArgumentException("longitude must be between -180 and 180");
            }
            Objects.requireNonNull(precision, "precision cannot be null");
        }

        public ResolvedLocation toResolvedLocation() {
            return new ResolvedLocation(latitude, longitude, displayName, precision);
        }
    }
}
