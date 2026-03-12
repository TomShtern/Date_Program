package datingapp.core.profile;

import datingapp.core.model.LocationModels;
import datingapp.core.model.LocationModels.City;
import datingapp.core.model.LocationModels.Country;
import datingapp.core.model.LocationModels.Precision;
import datingapp.core.model.LocationModels.ResolvedLocation;
import datingapp.core.model.LocationModels.ZipRange;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides built-in location lookup and reverse-display support without external
 * geocoding services.
 */
public final class LocationService {
    private static final String COUNTRY_IL = "IL";
    private static final String CITY_TEL_AVIV = "Tel Aviv";
    private static final String DISTRICT_TEL_AVIV = "Tel Aviv District";
    private static final String CITY_JERUSALEM = "Jerusalem";
    private static final String DISTRICT_JERUSALEM = "Jerusalem District";
    private static final String CITY_HAIFA = "Haifa";
    private static final String DISTRICT_HAIFA = "Haifa District";
    private static final String CITY_RISHON_LEZION = "Rishon LeZion";
    private static final String CITY_PETAH_TIKVA = "Petah Tikva";
    private static final String DISTRICT_CENTRAL = "Central District";
    private static final int DEFAULT_RESULT_LIMIT = 10;
    private static final double ZIP_MATCH_MAX_KM = 3.0;
    private static final double CITY_MATCH_MAX_KM = 12.0;

    private static final List<Country> COUNTRIES = List.of(
            new Country(COUNTRY_IL, "Israel", "🇮🇱", true, true),
            new Country("US", "United States", "🇺🇸", false, false),
            new Country("GB", "United Kingdom", "🇬🇧", false, false),
            new Country("CA", "Canada", "🇨🇦", false, false),
            new Country("AU", "Australia", "🇦🇺", false, false));

    private static final List<City> ISRAEL_CITIES = List.of(
            new City(CITY_TEL_AVIV, DISTRICT_TEL_AVIV, 32.0853, 34.7818, COUNTRY_IL, 1),
            new City(CITY_JERUSALEM, DISTRICT_JERUSALEM, 31.7683, 35.2137, COUNTRY_IL, 1),
            new City(CITY_HAIFA, DISTRICT_HAIFA, 32.7940, 34.9896, COUNTRY_IL, 1),
            new City(CITY_RISHON_LEZION, DISTRICT_CENTRAL, 31.9642, 34.8054, COUNTRY_IL, 1),
            new City(CITY_PETAH_TIKVA, DISTRICT_CENTRAL, 32.0870, 34.8877, COUNTRY_IL, 1),
            new City("Ashdod", "Southern District", 31.8044, 34.6553, COUNTRY_IL, 2),
            new City("Netanya", DISTRICT_CENTRAL, 32.3215, 34.8532, COUNTRY_IL, 2),
            new City("Be'er Sheva", "Southern District", 31.2518, 34.7913, COUNTRY_IL, 2),
            new City("Holon", DISTRICT_TEL_AVIV, 32.0117, 34.7738, COUNTRY_IL, 2),
            new City("Bnei Brak", DISTRICT_TEL_AVIV, 32.0808, 34.8338, COUNTRY_IL, 2),
            new City("Ramat Gan", DISTRICT_TEL_AVIV, 32.0703, 34.8267, COUNTRY_IL, 3),
            new City("Rehovot", DISTRICT_CENTRAL, 31.8934, 34.8100, COUNTRY_IL, 3),
            new City("Herzliya", DISTRICT_TEL_AVIV, 32.1667, 34.8500, COUNTRY_IL, 3),
            new City("Kfar Saba", DISTRICT_CENTRAL, 32.1742, 34.9067, COUNTRY_IL, 3),
            new City("Modi'in", DISTRICT_CENTRAL, 31.8969, 35.0061, COUNTRY_IL, 3));

    private static final List<ZipRange> ISRAEL_ZIP_RANGES = List.of(
            new ZipRange("6701", COUNTRY_IL, CITY_TEL_AVIV, DISTRICT_TEL_AVIV, 32.0650, 34.7700),
            new ZipRange("6702", COUNTRY_IL, CITY_TEL_AVIV, DISTRICT_TEL_AVIV, 32.0680, 34.7750),
            new ZipRange("6703", COUNTRY_IL, CITY_TEL_AVIV, DISTRICT_TEL_AVIV, 32.0700, 34.7800),
            new ZipRange("6704", COUNTRY_IL, CITY_TEL_AVIV, DISTRICT_TEL_AVIV, 32.0720, 34.7850),
            new ZipRange("6705", COUNTRY_IL, CITY_TEL_AVIV, DISTRICT_TEL_AVIV, 32.0750, 34.7900),
            new ZipRange("6706", COUNTRY_IL, CITY_TEL_AVIV, DISTRICT_TEL_AVIV, 32.0780, 34.7950),
            new ZipRange("6707", COUNTRY_IL, CITY_TEL_AVIV, DISTRICT_TEL_AVIV, 32.0800, 34.8000),
            new ZipRange("6708", COUNTRY_IL, CITY_TEL_AVIV, DISTRICT_TEL_AVIV, 32.0820, 34.8050),
            new ZipRange("6709", COUNTRY_IL, CITY_TEL_AVIV, DISTRICT_TEL_AVIV, 32.0850, 34.8100),
            new ZipRange("6710", COUNTRY_IL, CITY_TEL_AVIV, DISTRICT_TEL_AVIV, 32.0880, 34.8150),
            new ZipRange("9100", COUNTRY_IL, CITY_JERUSALEM, DISTRICT_JERUSALEM, 31.7683, 35.2137),
            new ZipRange("9101", COUNTRY_IL, CITY_JERUSALEM, DISTRICT_JERUSALEM, 31.7700, 35.2150),
            new ZipRange("9102", COUNTRY_IL, CITY_JERUSALEM, DISTRICT_JERUSALEM, 31.7720, 35.2170),
            new ZipRange("9103", COUNTRY_IL, CITY_JERUSALEM, DISTRICT_JERUSALEM, 31.7750, 35.2200),
            new ZipRange("9104", COUNTRY_IL, CITY_JERUSALEM, DISTRICT_JERUSALEM, 31.7780, 35.2230),
            new ZipRange("3100", COUNTRY_IL, CITY_HAIFA, DISTRICT_HAIFA, 32.7940, 34.9896),
            new ZipRange("3101", COUNTRY_IL, CITY_HAIFA, DISTRICT_HAIFA, 32.7960, 34.9910),
            new ZipRange("3102", COUNTRY_IL, CITY_HAIFA, DISTRICT_HAIFA, 32.7980, 34.9930),
            new ZipRange("7510", COUNTRY_IL, CITY_RISHON_LEZION, DISTRICT_CENTRAL, 31.9642, 34.8054),
            new ZipRange("7511", COUNTRY_IL, CITY_RISHON_LEZION, DISTRICT_CENTRAL, 31.9660, 34.8070),
            new ZipRange("4910", COUNTRY_IL, CITY_PETAH_TIKVA, DISTRICT_CENTRAL, 32.0870, 34.8877),
            new ZipRange("4911", COUNTRY_IL, CITY_PETAH_TIKVA, DISTRICT_CENTRAL, 32.0890, 34.8890));

    private final ValidationService validationService;

    public LocationService(ValidationService validationService) {
        this.validationService = Objects.requireNonNull(validationService, "validationService cannot be null");
    }

    public List<Country> getAvailableCountries() {
        return COUNTRIES;
    }

    public Country getDefaultCountry() {
        return COUNTRIES.stream().filter(Country::defaultSelection).findFirst().orElse(COUNTRIES.getFirst());
    }

    public List<City> getPopularCities(String countryCode) {
        return getPopularCities(countryCode, DEFAULT_RESULT_LIMIT);
    }

    public List<City> getPopularCities(String countryCode, int limit) {
        return citiesFor(countryCode).stream()
                .sorted(Comparator.comparingInt(City::priority).thenComparing(City::name))
                .limit(Math.max(1, limit))
                .toList();
    }

    public List<City> searchCities(String countryCode, String query) {
        return searchCities(countryCode, query, DEFAULT_RESULT_LIMIT);
    }

    public List<City> searchCities(String countryCode, String query, int limit) {
        if (query == null || query.isBlank()) {
            return getPopularCities(countryCode, limit);
        }
        String normalizedQuery = normalizeText(query);
        return citiesFor(countryCode).stream()
                .filter(city -> matches(city, normalizedQuery))
                .sorted(Comparator.comparingInt(City::priority).thenComparing(City::name))
                .limit(Math.max(1, limit))
                .toList();
    }

    public Optional<City> findCityByName(String countryCode, String cityName) {
        String normalizedName = normalizeText(cityName);
        return citiesFor(countryCode).stream()
                .filter(city -> normalizeText(city.name()).equals(normalizedName))
                .findFirst();
    }

    public ResolvedLocation resolveCity(City city) {
        Objects.requireNonNull(city, "city cannot be null");
        return new ResolvedLocation(city.latitude(), city.longitude(), city.displayName(), Precision.CITY);
    }

    public ZipLookupResult lookupZip(String countryCode, String zipCode) {
        ValidationService.ValidationResult validationResult = validationService.validateZipCode(zipCode, countryCode);
        if (!validationResult.valid()) {
            return ZipLookupResult.invalid(validationResult.errors().getFirst());
        }
        String normalizedZip = normalizeZip(zipCode);
        String prefix = normalizedZip.substring(0, 4);
        return zipRangesFor(countryCode).stream()
                .filter(range -> range.prefix().equals(prefix))
                .findFirst()
                .map(range -> ZipLookupResult.supported(
                        prefix,
                        new ResolvedLocation(range.latitude(), range.longitude(), range.displayName(), Precision.ZIP)))
                .orElseGet(() -> ZipLookupResult.unsupported(
                        prefix, "ZIP code format is valid, but this area is not supported yet."));
    }

    public Optional<ResolvedLocation> reverseLookup(double latitude, double longitude) {
        Optional<ResolvedLocation> zipMatch = zipRangesFor(COUNTRY_IL).stream()
                .map(range -> new CandidateMatch(
                        distanceKm(latitude, longitude, range.latitude(), range.longitude()),
                        new ResolvedLocation(range.latitude(), range.longitude(), range.displayName(), Precision.ZIP)))
                .filter(match -> match.distanceKm() <= ZIP_MATCH_MAX_KM)
                .min(Comparator.comparingDouble(CandidateMatch::distanceKm))
                .map(CandidateMatch::location);
        if (zipMatch.isPresent()) {
            return zipMatch;
        }
        return citiesFor(COUNTRY_IL).stream()
                .map(city -> new CandidateMatch(
                        distanceKm(latitude, longitude, city.latitude(), city.longitude()), resolveCity(city)))
                .filter(match -> match.distanceKm() <= CITY_MATCH_MAX_KM)
                .min(Comparator.comparingDouble(CandidateMatch::distanceKm))
                .map(CandidateMatch::location);
    }

    public String formatForDisplay(double latitude, double longitude) {
        return reverseLookup(latitude, longitude)
                .map(ResolvedLocation::label)
                .orElse(LocationModels.formatCoordinates(latitude, longitude));
    }

    private List<City> citiesFor(String countryCode) {
        return COUNTRY_IL.equalsIgnoreCase(countryCode) ? ISRAEL_CITIES : List.of();
    }

    private List<ZipRange> zipRangesFor(String countryCode) {
        return COUNTRY_IL.equalsIgnoreCase(countryCode) ? ISRAEL_ZIP_RANGES : List.of();
    }

    private boolean matches(City city, String normalizedQuery) {
        return normalizeText(city.name()).contains(normalizedQuery)
                || normalizeText(city.district()).contains(normalizedQuery);
    }

    private static String normalizeText(String text) {
        String normalized = text == null ? "" : text.trim();
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC);
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeZip(String zipCode) {
        return zipCode == null ? "" : zipCode.replaceAll("[\\s-]", "");
    }

    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(deltaLon / 2)
                        * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    public record ZipLookupResult(
            boolean valid, String message, String normalizedZip, Optional<ResolvedLocation> resolvedLocation) {
        public ZipLookupResult {
            message = message == null ? "" : message;
            normalizedZip = normalizedZip == null ? "" : normalizedZip;
            Objects.requireNonNull(resolvedLocation, "resolvedLocation cannot be null");
        }

        public static ZipLookupResult invalid(String message) {
            return new ZipLookupResult(false, message, "", Optional.empty());
        }

        public static ZipLookupResult supported(String normalizedZip, ResolvedLocation resolvedLocation) {
            return new ZipLookupResult(true, "", normalizedZip, Optional.of(resolvedLocation));
        }

        public static ZipLookupResult unsupported(String normalizedZip, String message) {
            return new ZipLookupResult(true, message, normalizedZip, Optional.empty());
        }
    }

    private record CandidateMatch(double distanceKm, ResolvedLocation location) {}
}
