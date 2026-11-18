package tt.sheets;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Orden de despliegue: b-sheet puede llegar a producción antes de que se aplique en la BD de TT_BKN la
 * migración que crea analisis.id_cuenta. Se renombra la columna en la BD de tests (y se restaura al final)
 * y se arranca un contexto nuevo: nada falla, autor = null y seguimiento vacío.
 */
class AutorSinColumnaApiTest {

    private static final String URL = env("DB_TEST_URL", "jdbc:postgresql://localhost:5432/sheets_test");
    private static final String USER = env("DB_USER", System.getProperty("user.name"));
    private static final String PASSWORD = env("DB_PASSWORD", "");

    @Test
    void withoutTheColumnAuthorsAreNullAndFollowUpIsEmpty() throws SQLException {
        execute("ALTER TABLE analisis RENAME COLUMN id_cuenta TO id_cuenta_sin_migrar");
        try (LambdaTestSupport lambda = new LambdaTestSupport()) {
            Map<String, String> auth = lambda.bearer("demo@sheets.tt");

            APIGatewayV2HTTPResponse lista = lambda.get("/analisis", auth);
            assertEquals(200, lista.getStatusCode(), LambdaTestSupport.body(lista));
            List<Map<String, Object>> analisis = lambda.jsonList(lista);
            assertEquals(6, analisis.size());
            for (Map<String, Object> a : analisis) {
                assertEquals(true, a.containsKey("autor"));
                assertEquals(null, a.get("autor"));
            }
            assertEquals(null, lambda.json(lambda.get("/analisis/e0000000-0000-0000-0000-000000000001", auth)).get("autor"));

            Map<String, Object> arbol = lambda.json(lambda.get("/arboles/b0000000-0000-0000-0000-000000000001", auth));
            assertEquals(List.of(), arbol.get("seguimiento"));
            // registradoPor sale de arbol.id_cuenta, que ya existía: sigue funcionando.
            assertEquals("Usuario Demo", ((Map<?, ?>) arbol.get("registradoPor")).get("nombre"));

            APIGatewayV2HTTPResponse zona = lambda.get("/zonas/c0000000-0000-0000-0000-000000000001", auth);
            assertEquals(200, zona.getStatusCode(), LambdaTestSupport.body(zona));
            assertEquals(List.of(), lambda.json(zona).get("seguimiento"));
        } finally {
            execute("ALTER TABLE analisis RENAME COLUMN id_cuenta_sin_migrar TO id_cuenta");
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
