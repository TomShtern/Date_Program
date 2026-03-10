package datingapp.core.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class I18nTest {

    @Test
    @DisplayName("bundle is available for the default locale")
    void bundleIsAvailableForDefaultLocale() {
        assertNotNull(I18n.bundle());
    }

    @Test
    @DisplayName("formats keyed messages with arguments")
    void formatsKeyedMessagesWithArguments() {
        assertEquals("You and Riley liked each other!", I18n.text("ui.match.dialog.message", "Riley"));
    }
}
