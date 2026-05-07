package datingapp.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LocationPackageArchitectureTest {

    private static final Path TEST_ROOT = Path.of("src", "test", "java");
    private static final String EXPECTED_SEGMENT = "/src/test/java/datingapp/location/";
    private static final Set<String> LOCATION_UNIT_TEST_FILES = Set.of(
            "LocationModelsTest.java",
            "LocationServiceTest.java",
            "LocalGeocodingServiceTest.java",
            "NominatimGeocodingServiceTest.java");

    @Test
    void pureLocationUnitTestsLiveUnderLocationPackage() throws IOException {
        List<String> wrongLocations;
        try (Stream<Path> paths = Files.walk(TEST_ROOT)) {
            wrongLocations = paths.filter(path ->
                            LOCATION_UNIT_TEST_FILES.contains(path.getFileName().toString()))
                    .map(path -> path.toAbsolutePath().normalize().toString().replace('\\', '/'))
                    .filter(path -> !path.contains(EXPECTED_SEGMENT))
                    .toList();
        }

        assertTrue(
                wrongLocations.isEmpty(),
                "Pure location unit tests should live under datingapp/location:\n" + String.join("\n", wrongLocations));
    }
}
