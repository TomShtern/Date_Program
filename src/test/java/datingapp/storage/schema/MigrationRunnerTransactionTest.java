package datingapp.storage.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MigrationRunner transaction handling")
class MigrationRunnerTransactionTest {

    @Test
    @DisplayName("runWithMigrationTransaction preserves work failure when auto-commit restore also fails")
    void runWithMigrationTransactionPreservesPrimaryFailureWhenRestoreAlsoFails() throws Exception {
        SQLException primaryFailure = new SQLException("primary work failure");
        SQLException restoreFailure = new SQLException("restore auto-commit failure");
        Method method = runWithMigrationTransactionMethod();

        InvocationTargetException invocation = assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(
                        null, statementProxy(connectionProxy(restoreFailure)), sqlWorkProxy(primaryFailure)));

        Throwable cause = invocation.getCause();
        assertSame(primaryFailure, cause);
        assertEquals(1, cause.getSuppressed().length);
        assertSame(restoreFailure, cause.getSuppressed()[0]);
    }

    private static Method runWithMigrationTransactionMethod() throws Exception {
        Class<?> sqlWorkType = Class.forName("datingapp.storage.schema.MigrationRunner$SqlWork");
        Method method =
                MigrationRunner.class.getDeclaredMethod("runWithMigrationTransaction", Statement.class, sqlWorkType);
        method.setAccessible(true);
        return method;
    }

    private static Object sqlWorkProxy(SQLException primaryFailure) throws Exception {
        Class<?> sqlWorkType = Class.forName("datingapp.storage.schema.MigrationRunner$SqlWork");
        InvocationHandler handler = (proxy, method, args) -> {
            if ("run".equals(method.getName())) {
                throw primaryFailure;
            }
            return defaultObjectMethod(proxy, method, args);
        };
        return Proxy.newProxyInstance(sqlWorkType.getClassLoader(), new Class<?>[] {sqlWorkType}, handler);
    }

    private static Statement statementProxy(Connection connection) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("getConnection".equals(method.getName())) {
                return connection;
            }
            return defaultObjectMethod(proxy, method, args);
        };
        return (Statement)
                Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[] {Statement.class}, handler);
    }

    private static Connection connectionProxy(SQLException restoreFailure) {
        InvocationHandler handler = new InvocationHandler() {
            private boolean autoCommit = true;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                return switch (method.getName()) {
                    case "getAutoCommit" -> autoCommit;
                    case "setAutoCommit" -> handleSetAutoCommit((boolean) args[0]);
                    case "rollback", "commit", "close" -> null;
                    default -> defaultObjectMethod(proxy, method, args);
                };
            }

            private Object handleSetAutoCommit(boolean requestedAutoCommit) throws SQLException {
                if (requestedAutoCommit) {
                    throw restoreFailure;
                }
                autoCommit = false;
                return null;
            }
        };
        return (Connection)
                Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
    }

    private static Object defaultObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> proxy.getClass().getName();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }
}
