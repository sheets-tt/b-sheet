package tt.sheets;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** GET /estadisticas: resumen del dashboard, requiere JWT. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EstadisticasApiTest {

    private LambdaTestSupport lambda;

    @BeforeAll
    void start() {
        lambda = new LambdaTestSupport();
    }

    @AfterAll
    void stop() {
        lambda.close();
    }

    @Test
    void requiresToken() {
        assertEquals(401, lambda.get("/estadisticas", Map.of()).getStatusCode());
    }

    @Test
    void returnsContractPayload() {
        APIGatewayV2HTTPResponse res = lambda.get("/estadisticas", lambda.bearer("demo@sheets.tt"));
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        assertEquals(Map.of(
                "totales", Map.of("arboles", 3, "zonas", 2, "analisis", 5, "dispositivos", 1,
                        "arbolesConInfestacion", 1, "zonasConInfestacion", 1),
                "analisisPorResultado", Map.of("sano", 2, "infestado", 2, "no_determinado", 1),
                "analisisPorTipo", Map.of("corteza", 2, "follaje", 3),
                "analisisPorSincronizacion", Map.of("pendiente", 1, "sincronizado", 3, "error", 1),
                "cromaticoPromedio", Map.of("pctSano", 55.0, "pctRegular", 15.0, "pctDanado", 7.5, "pctIncierto", 22.5),
                "ultimaCaptura", "2026-10-08T11:00:00Z"), lambda.json(res));
    }

    @Test
    void categoriesKeepDeclarationOrder() {
        // Las gráficas de la web dependen del orden: sano, infestado, no_determinado.
        APIGatewayV2HTTPResponse res = lambda.get("/estadisticas", lambda.bearer("demo@sheets.tt"));
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        String body = LambdaTestSupport.body(res);
        List<Integer> posiciones = List.of(body.indexOf("\"sano\""), body.indexOf("\"infestado\""),
                body.indexOf("\"no_determinado\""));
        assertEquals(false, posiciones.contains(-1), body);
        assertEquals(posiciones.stream().sorted().toList(), posiciones, body);
    }
}
