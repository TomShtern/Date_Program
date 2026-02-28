package datingapp.app.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import org.junit.jupiter.api.Test;

class AppErrorTest {

    @Test
    void validationCarriesFieldName() {
        var error = new AppError.Validation("Name is required", "name");
        assertEquals("Name is required", error.message());
        assertEquals("name", error.field());
    }

    @Test
    void notFoundCarriesResourceInfo() {
        var error = new AppError.NotFound("User not found", "User", "abc-123");
        assertEquals("User not found", error.message());
        assertEquals("User", error.resourceType());
        assertEquals("abc-123", error.resourceId());
    }

    @Test
    void infrastructureCarriesCause() {
        var cause = new RuntimeException("DB down");
        var error = new AppError.Infrastructure("Database failure", cause);
        assertEquals("Database failure", error.message());
        assertEquals(cause, error.cause());
    }

    @Test
    void infrastructureAllowsNullCause() {
        var error = new AppError.Infrastructure("Unknown failure", null);
        assertNull(error.cause());
    }

    @Test
    void fromUseCaseErrorRoundTrips() {
        for (UseCaseError.Code code : UseCaseError.Code.values()) {
            var original = new UseCaseError(code, "test message for " + code);
            AppError appError = AppError.fromUseCaseError(original);
            UseCaseError roundTripped = appError.toUseCaseError();

            assertEquals(original.code(), roundTripped.code());
            assertEquals(original.message(), roundTripped.message());
        }
    }

    @Test
    void appResultMutualExclusivity() {
        assertThrows(IllegalArgumentException.class, () -> new AppResult<>("data", new AppError.Conflict("conflict")));
    }

    @Test
    void appResultOkHasNoError() {
        AppResult<String> result = AppResult.ok("data");
        assertTrue(result.success());
        assertEquals("data", result.data());
        assertNull(result.error());
    }

    @Test
    void appResultFailHasNoData() {
        AppResult<String> result = AppResult.fail(new AppError.Conflict("conflict"));
        assertFalse(result.success());
        assertNull(result.data());
        assertNotNull(result.error());
    }

    @Test
    void appResultOkVoidAllowed() {
        AppResult<Void> result = AppResult.ok();
        assertTrue(result.success());
        assertNull(result.data());
        assertNull(result.error());
    }

    @Test
    void appResultBridgeFromUseCaseResult() {
        UseCaseResult<String> success = UseCaseResult.success("hello");
        AppResult<String> appResult = AppResult.fromUseCaseResult(success);
        assertTrue(appResult.success());
        assertEquals("hello", appResult.data());

        UseCaseResult<String> failure = UseCaseResult.failure(UseCaseError.conflict("oops"));
        AppResult<String> failResult = AppResult.fromUseCaseResult(failure);
        assertFalse(failResult.success());
        assertTrue(failResult.error() instanceof AppError.Conflict);
    }

    @Test
    void appResultBridgeToUseCaseResult() {
        AppResult<String> ok = AppResult.ok("world");
        UseCaseResult<String> ucResult = ok.toUseCaseResult();
        assertTrue(ucResult.success());
        assertEquals("world", ucResult.data());

        AppResult<String> fail = AppResult.fail(new AppError.Forbidden("denied"));
        UseCaseResult<String> ucFail = fail.toUseCaseResult();
        assertFalse(ucFail.success());
        assertEquals(UseCaseError.Code.FORBIDDEN, ucFail.error().code());
    }
}
