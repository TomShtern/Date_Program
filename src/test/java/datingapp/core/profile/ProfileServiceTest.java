package datingapp.core.profile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import datingapp.core.AppConfig;
import datingapp.core.testutil.TestStorages;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileService")
class ProfileServiceTest {

    @Test
    @DisplayName("caches the achievement service instance")
    void cachesAchievementServiceInstance() throws ReflectiveOperationException {
        ProfileService service = new ProfileService(
                AppConfig.defaults(),
                new TestStorages.Analytics(),
                new TestStorages.Interactions(),
                new TestStorages.TrustSafety(),
                new TestStorages.Users());

        Object first = achievementService(service);
        Object second = achievementService(service);

        assertNotNull(first);
        assertSame(first, second);
    }

    private static Object achievementService(ProfileService service) throws ReflectiveOperationException {
        Field field = ProfileService.class.getDeclaredField("achievementService");
        field.setAccessible(true);
        return field.get(service);
    }
}
