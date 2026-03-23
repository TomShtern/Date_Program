package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestStorages;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileService")
class ProfileServiceTest {

    @Test
    @DisplayName("caches the legacy achievement service instance")
    void cachesLegacyAchievementServiceInstance() throws ReflectiveOperationException {
        ProfileService service = new ProfileService(
                AppConfig.defaults(),
                new TestStorages.Analytics(),
                new TestStorages.Interactions(),
                new TestStorages.TrustSafety(),
                new TestStorages.Users());

        Object first = legacyAchievementService(service);
        Object second = legacyAchievementService(service);

        assertNotNull(first);
        assertSame(first, second);
    }

    private static Object legacyAchievementService(ProfileService service) throws ReflectiveOperationException {
        Field field = ProfileService.class.getDeclaredField("legacyAchievementService");
        field.setAccessible(true);
        return field.get(service);
    }
}
