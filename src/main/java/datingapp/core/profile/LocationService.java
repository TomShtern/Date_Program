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
    private static final String RESOLVED_LOCATION_REQUIRED = "resolvedLocation cannot be null";
    private static final String COUNTRY_IL = "IL";
    private static final String APPROXIMATE_ISRAEL_LABEL = "Approximate area in Israel";
    private static final String CITY_TEL_AVIV = "Tel Aviv";
    private static final String DISTRICT_TEL_AVIV = "Tel Aviv District";
    private static final String CITY_JERUSALEM = "Jerusalem";
    private static final String DISTRICT_JERUSALEM = "Jerusalem District";
    private static final String CITY_HAIFA = "Haifa";
    private static final String DISTRICT_HAIFA = "Haifa District";
    private static final String CITY_RISHON_LEZION = "Rishon LeZion";
    private static final String CITY_PETAH_TIKVA = "Petah Tikva";
    private static final String DISTRICT_CENTRAL = "Central District";
    private static final String DISTRICT_NORTHERN = "Northern District";
    private static final String DISTRICT_SOUTHERN = "Southern District";
    private static final String DISTRICT_JUDEA_SAMARIA = "Judea and Samaria Area";
    private static final int DEFAULT_RESULT_LIMIT = 10;
    private static final double ZIP_MATCH_MAX_KM = 3.0;
    private static final double CITY_MATCH_MAX_KM = 12.0;
    private static final double APPROXIMATE_IL_LATITUDE = 31.4117;
    private static final double APPROXIMATE_IL_LONGITUDE = 35.0818;

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
            new City("Ashdod", DISTRICT_SOUTHERN, 31.8044, 34.6553, COUNTRY_IL, 2),
            new City("Netanya", DISTRICT_CENTRAL, 32.3215, 34.8532, COUNTRY_IL, 2),
            new City("Be'er Sheva", DISTRICT_SOUTHERN, 31.2518, 34.7913, COUNTRY_IL, 2),
            new City("Holon", DISTRICT_TEL_AVIV, 32.0117, 34.7738, COUNTRY_IL, 2),
            new City("Bnei Brak", DISTRICT_TEL_AVIV, 32.0808, 34.8338, COUNTRY_IL, 2),
            new City("Ramat Gan", DISTRICT_TEL_AVIV, 32.0703, 34.8267, COUNTRY_IL, 3),
            new City("Rehovot", DISTRICT_CENTRAL, 31.8934, 34.8100, COUNTRY_IL, 3),
            new City("Herzliya", DISTRICT_TEL_AVIV, 32.1667, 34.8500, COUNTRY_IL, 3),
            new City("Kfar Saba", DISTRICT_CENTRAL, 32.1742, 34.9067, COUNTRY_IL, 3),
            new City("Modi'in", DISTRICT_CENTRAL, 31.8969, 35.0061, COUNTRY_IL, 3),
            new City("Ashkelon", DISTRICT_SOUTHERN, 31.6688, 34.5743, COUNTRY_IL, 4),
            new City("Bat Yam", DISTRICT_TEL_AVIV, 32.0238, 34.7503, COUNTRY_IL, 4),
            new City("Hadera", DISTRICT_HAIFA, 32.4340, 34.9197, COUNTRY_IL, 4),
            new City("Lod", DISTRICT_CENTRAL, 31.9522, 34.8884, COUNTRY_IL, 4),
            new City("Ramla", DISTRICT_CENTRAL, 31.9302, 34.8656, COUNTRY_IL, 4),
            new City("Ra'anana", DISTRICT_CENTRAL, 32.1848, 34.8713, COUNTRY_IL, 4),
            new City("Hod HaSharon", DISTRICT_CENTRAL, 32.1592, 34.8932, COUNTRY_IL, 4),
            new City("Givatayim", DISTRICT_TEL_AVIV, 32.0723, 34.8113, COUNTRY_IL, 4),
            new City("Or Yehuda", DISTRICT_TEL_AVIV, 32.0290, 34.8578, COUNTRY_IL, 4),
            new City("Beit Shemesh", DISTRICT_JERUSALEM, 31.7497, 34.9886, COUNTRY_IL, 4),
            new City("Yavne", DISTRICT_CENTRAL, 31.8781, 34.7398, COUNTRY_IL, 4),
            new City("Rosh HaAyin", DISTRICT_CENTRAL, 32.0956, 34.9566, COUNTRY_IL, 4),
            new City("Nahariya", DISTRICT_NORTHERN, 33.0080, 35.0981, COUNTRY_IL, 4),
            new City("Acre", DISTRICT_NORTHERN, 32.9234, 35.0818, COUNTRY_IL, 4),
            new City("Carmiel", DISTRICT_NORTHERN, 32.9190, 35.3046, COUNTRY_IL, 4),
            new City("Tiberias", DISTRICT_NORTHERN, 32.7940, 35.5312, COUNTRY_IL, 4),
            new City("Nazareth", DISTRICT_NORTHERN, 32.6996, 35.3035, COUNTRY_IL, 4),
            new City("Afula", DISTRICT_NORTHERN, 32.6091, 35.2892, COUNTRY_IL, 4),
            new City("Tirat Carmel", DISTRICT_HAIFA, 32.7618, 34.9715, COUNTRY_IL, 4),
            new City("Nesher", DISTRICT_HAIFA, 32.7650, 35.0500, COUNTRY_IL, 4),
            new City("Kiryat Ata", DISTRICT_HAIFA, 32.8115, 35.1137, COUNTRY_IL, 4),
            new City("Kiryat Bialik", DISTRICT_HAIFA, 32.8271, 35.0871, COUNTRY_IL, 4),
            new City("Kiryat Motzkin", DISTRICT_HAIFA, 32.8356, 35.0770, COUNTRY_IL, 4),
            new City("Kiryat Yam", DISTRICT_HAIFA, 32.8486, 35.0665, COUNTRY_IL, 4),
            new City("Kiryat Gat", DISTRICT_SOUTHERN, 31.6096, 34.7642, COUNTRY_IL, 4),
            new City("Sderot", DISTRICT_SOUTHERN, 31.5242, 34.5958, COUNTRY_IL, 4),
            new City("Netivot", DISTRICT_SOUTHERN, 31.4231, 34.5890, COUNTRY_IL, 4),
            new City("Ofakim", DISTRICT_SOUTHERN, 31.3141, 34.6202, COUNTRY_IL, 4),
            new City("Dimona", DISTRICT_SOUTHERN, 31.0708, 35.0333, COUNTRY_IL, 4),
            new City("Arad", DISTRICT_SOUTHERN, 31.2589, 35.2128, COUNTRY_IL, 4),
            new City("Eilat", DISTRICT_SOUTHERN, 29.5577, 34.9519, COUNTRY_IL, 4),
            new City("Kiryat Shmona", DISTRICT_NORTHERN, 33.2073, 35.5697, COUNTRY_IL, 4),
            new City("Ma'ale Adumim", DISTRICT_JERUSALEM, 31.7680, 35.3015, COUNTRY_IL, 4),
            new City("Ariel", DISTRICT_JUDEA_SAMARIA, 32.1047, 35.1889, COUNTRY_IL, 4),
            new City("Pardes Hanna-Karkur", DISTRICT_HAIFA, 32.4728, 34.9774, COUNTRY_IL, 4));

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
        return COUNTRIES.stream().filter(Country::available).toList();
    }

    public Optional<Country> findCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return Optional.empty();
        }
        return COUNTRIES.stream()
                .filter(country -> country.code().equalsIgnoreCase(countryCode.trim()))
                .findFirst();
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

    public ResolveSelectionResult resolveSelection(
            String countryCode, String cityName, String zipCode, boolean allowApproximate) {
        Country country = findCountry(countryCode).orElse(null);
        if (country == null) {
            return ResolveSelectionResult.invalid("Country is required");
        }
        if (!country.available()) {
            return ResolveSelectionResult.invalid(country.name() + " is not supported yet.");
        }

        if (cityName != null && !cityName.isBlank()) {
            return findCityByName(country.code(), cityName)
                    .map(this::resolveCity)
                    .map(ResolveSelectionResult::supported)
                    .orElseGet(() -> ResolveSelectionResult.invalid("Selected city is not supported."));
        }

        if (zipCode == null || zipCode.isBlank()) {
            return ResolveSelectionResult.invalid("Choose a city or ZIP code first.");
        }

        ZipLookupResult zipLookupResult = lookupZip(country.code(), zipCode);
        if (!zipLookupResult.valid()) {
            return ResolveSelectionResult.invalid(zipLookupResult.message());
        }
        if (zipLookupResult.resolvedLocation().isPresent()) {
            return ResolveSelectionResult.supported(
                    zipLookupResult.resolvedLocation().orElseThrow(), zipLookupResult.message());
        }
        if (!allowApproximate) {
            return ResolveSelectionResult.invalid(zipLookupResult.message());
        }
        return approximateZipFallback(country.code(), zipLookupResult.normalizedZip());
    }

    public Optional<SelectionSeed> seedSelection(double latitude, double longitude) {
        Country country = getDefaultCountry();
        Optional<ZipRange> zipRange = findZipRangeForCoordinates(latitude, longitude);
        if (zipRange.isPresent()) {
            ZipRange range = zipRange.orElseThrow();
            return Optional.of(new SelectionSeed(
                    country,
                    findCityByName(range.countryCode(), range.city()),
                    Optional.of(range.prefix()),
                    new ResolvedLocation(range.latitude(), range.longitude(), range.displayName(), Precision.ZIP)));
        }

        Optional<City> city = findCityForCoordinates(latitude, longitude);
        if (city.isPresent()) {
            City matchedCity = city.orElseThrow();
            return Optional.of(new SelectionSeed(
                    country,
                    Optional.of(matchedCity),
                    Optional.empty(),
                    new ResolvedLocation(
                            matchedCity.latitude(),
                            matchedCity.longitude(),
                            matchedCity.displayName(),
                            Precision.CITY)));
        }

        return Optional.empty();
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
        Optional<ResolvedLocation> zipMatch = findZipRangeForCoordinates(latitude, longitude)
                .map(range ->
                        new ResolvedLocation(range.latitude(), range.longitude(), range.displayName(), Precision.ZIP));
        if (zipMatch.isPresent()) {
            return zipMatch;
        }
        return findCityForCoordinates(latitude, longitude).map(this::resolveCity);
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

    private ResolveSelectionResult approximateZipFallback(String countryCode, String normalizedZip) {
        if (!COUNTRY_IL.equalsIgnoreCase(countryCode)) {
            return ResolveSelectionResult.invalid("Approximate ZIP fallback is not available for this country yet.");
        }
        String message = normalizedZip == null || normalizedZip.isBlank()
                ? "Using an approximate supported area in Israel."
                : "Using an approximate supported area in Israel for ZIP " + normalizedZip + ".";
        return ResolveSelectionResult.approximate(
                new ResolvedLocation(
                        APPROXIMATE_IL_LATITUDE, APPROXIMATE_IL_LONGITUDE, APPROXIMATE_ISRAEL_LABEL, Precision.ZIP),
                message);
    }

    private Optional<ZipRange> findZipRangeForCoordinates(double latitude, double longitude) {
        return zipRangesFor(COUNTRY_IL).stream()
                .map(range -> new ZipRangeCandidate(
                        distanceKm(latitude, longitude, range.latitude(), range.longitude()), range))
                .filter(candidate -> candidate.distanceKm() <= ZIP_MATCH_MAX_KM)
                .min(Comparator.comparingDouble(ZipRangeCandidate::distanceKm))
                .map(ZipRangeCandidate::range);
    }

    private Optional<City> findCityForCoordinates(double latitude, double longitude) {
        return citiesFor(COUNTRY_IL).stream()
                .map(city ->
                        new CityCandidate(distanceKm(latitude, longitude, city.latitude(), city.longitude()), city))
                .filter(candidate -> candidate.distanceKm() <= CITY_MATCH_MAX_KM)
                .min(Comparator.comparingDouble(CityCandidate::distanceKm))
                .map(CityCandidate::city);
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
            Objects.requireNonNull(resolvedLocation, RESOLVED_LOCATION_REQUIRED);
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

    public record ResolveSelectionResult(
            boolean valid, String message, Optional<ResolvedLocation> resolvedLocation, boolean approximate) {
        public ResolveSelectionResult {
            message = message == null ? "" : message;
            Objects.requireNonNull(resolvedLocation, RESOLVED_LOCATION_REQUIRED);
        }

        public static ResolveSelectionResult invalid(String message) {
            return new ResolveSelectionResult(false, message, Optional.empty(), false);
        }

        public static ResolveSelectionResult supported(ResolvedLocation resolvedLocation) {
            return supported(resolvedLocation, "");
        }

        public static ResolveSelectionResult supported(ResolvedLocation resolvedLocation, String message) {
            return new ResolveSelectionResult(true, message, Optional.of(resolvedLocation), false);
        }

        public static ResolveSelectionResult approximate(ResolvedLocation resolvedLocation, String message) {
            return new ResolveSelectionResult(true, message, Optional.of(resolvedLocation), true);
        }
    }

    public record SelectionSeed(
            Country country, Optional<City> city, Optional<String> zipPrefix, ResolvedLocation resolvedLocation) {
        public SelectionSeed {
            Objects.requireNonNull(country, "country cannot be null");
            Objects.requireNonNull(city, "city cannot be null");
            Objects.requireNonNull(zipPrefix, "zipPrefix cannot be null");
            Objects.requireNonNull(resolvedLocation, RESOLVED_LOCATION_REQUIRED);
        }
    }

    private record ZipRangeCandidate(double distanceKm, ZipRange range) {}

    private record CityCandidate(double distanceKm, City city) {}
}
