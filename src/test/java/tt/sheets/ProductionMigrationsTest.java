package tt.sheets;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Las migraciones de producción (db/migration, las que llegan a Neon) no deben crear cuentas. */
class ProductionMigrationsTest {

    private static final String URL = env("DB_TEST_URL", "jdbc:postgresql://localhost:5432/sheets_test");
    private static final String USER = env("DB_USER", System.getProperty("user.name"));
    private static final String PASSWORD = env("DB_PASSWORD", "");
    private static final String SCHEMA = "prod_migrations_check";

    @Test
    void productionMigrationsCreateNoAccounts() throws SQLException {
        try {
            Flyway.configure()
                    .dataSource(URL, USER, PASSWORD)
                    .schemas(SCHEMA)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            assertEquals(0, countAccounts(), "db/migration no debe insertar cuentas: van a producción");
        } finally {
            execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private static int countAccounts() throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + SCHEMA + ".cuenta")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
