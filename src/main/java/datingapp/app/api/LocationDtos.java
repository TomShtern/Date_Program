package datingapp.app.api;

import datingapp.core.model.LocationModels.City;
import datingapp.core.model.LocationModels.Country;
import datingapp.core.model.LocationModels.ResolvedLocation;

final class LocationDtos {
    private LocationDtos() {}

    /** Country DTO for location metadata responses. */
    static record LocationCountryDto(
            String code, String name, String flagEmoji, boolean available, boolean defaultSelection) {
        static LocationCountryDto from(Country country) {
            return new LocationCountryDto(
                    country.code(),
                    country.name(),
                    country.flagEmoji(),
                    country.available(),
                    country.defaultSelection());
        }
    }

    /** City DTO for location metadata responses. */
    static record LocationCityDto(String name, String district, String countryCode, int priority) {
        static LocationCityDto from(City city) {
            return new LocationCityDto(city.name(), city.district(), city.countryCode(), city.priority());
        }
    }

    /** Request body for resolving a location selection. */
    static record LocationResolveRequest(
            String countryCode, String cityName, String zipCode, Boolean allowApproximate) {}

    /** Response body for resolving a location selection. */
    static record LocationResolveResponse(
            String label, double latitude, double longitude, String precision, boolean approximate, String message) {
        static LocationResolveResponse from(ResolvedLocation location, boolean approximate, String message) {
            return new LocationResolveResponse(
                    location.label(),
                    location.latitude(),
                    location.longitude(),
                    location.precision().name(),
                    approximate,
                    message == null ? "" : message);
        }
    }

    /** Nested location input for selection-based profile updates. */
    static record ProfileLocationRequest(
            String countryCode, String cityName, String zipCode, Boolean allowApproximate) {}
}
