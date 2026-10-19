package tt.sheets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "FLYWAY_URL", matches = ".+")
class NeonDatabaseTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    @Test
    void everyMigrationInTheRepoIsAppliedInNeon() throws SQLException, IOException {
        assertEquals(repoVersions(), appliedVersions(), "Neon no tiene todas las migraciones: corre flyway migrate");
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("FLYWAY_URL"),
                System.getenv("FLYWAY_USER"), System.getenv("FLYWAY_PASSWORD"));
    }

    /** Versiones de los archivos V{n}__*.sql, en orden numérico. */
    private static List<String> repoVersions() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            return files.map(f -> f.getFileName().toString())
                    .filter(name -> name.matches("V\\d+__.*\\.sql"))
                    .map(name -> name.substring(1, name.indexOf("__")))
                    .sorted(Comparator.comparingInt(Integer::parseInt))
                    .toList();
        }
    }

    /** Versiones aplicadas con éxito según Flyway; vacío si Flyway nunca corrió en esa BD. */
    private static List<String> appliedVersions() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            try (ResultSet table = statement.executeQuery("SELECT to_regclass('flyway_schema_history') IS NOT NULL")) {
                table.next();
                if (!table.getBoolean(1)) {
                    return List.of();
                }
            }
            List<String> versions = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery(
                    "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank")) {
                while (rows.next()) {
                    versions.add(rows.getString(1));
                }
            }
            return versions;
        }
    }
}
