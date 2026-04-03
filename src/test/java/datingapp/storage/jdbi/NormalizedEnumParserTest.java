package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Normalized enum parser cleanup")
class NormalizedEnumParserTest {

    @Test
    @DisplayName("legacy local parseEnumNames helpers are removed from normalized profile collaborators")
    void legacyLocalParseEnumNamesHelpersAreRemovedFromNormalizedProfileCollaborators() {
        assertThrows(
                NoSuchMethodException.class,
                () -> NormalizedProfileHydrator.class.getDeclaredMethod(
                        "parseEnumNames", Collection.class, Class.class));
        assertThrows(
                NoSuchMethodException.class,
                () -> DealbreakerAssembler.class.getDeclaredMethod("parseEnumNames", Collection.class, Class.class));
    }
}
