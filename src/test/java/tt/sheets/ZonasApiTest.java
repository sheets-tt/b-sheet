package tt.sheets;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** GET /zonas y GET /zonas/{id}: solo lectura, requieren JWT. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZonasApiTest {

    private static final String LAGO_ID = "c0000000-0000-0000-0000-000000000001";
    private static final String CALZADA_ID = "c0000000-0000-0000-0000-000000000002";

    private LambdaTestSupport lambda;
    private Map<String, String> auth;

    @BeforeAll
    void start() {
        lambda = new LambdaTestSupport();
        auth = lambda.bearer("demo@sheets.tt");
    }

    @AfterAll
    void stop() {
        lambda.close();
    }

    @Test
    void listRequiresToken() {
        assertEquals(401, lambda.get("/zonas", Map.of()).getStatusCode());
    }

    @Test
    void listReturnsZonesMostRecentFirst() {
        APIGatewayV2HTTPResponse res = lambda.get("/zonas", auth);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        assertEquals(List.of(CALZADA_ID, LAGO_ID), lambda.jsonList(res).stream().map(z -> z.get("id")).toList());
    }

    @Test
    void detailReturnsContractPayload() {
        APIGatewayV2HTTPResponse res = lambda.get("/zonas/" + LAGO_ID, auth);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        Map<String, Object> esperado = new HashMap<>();
        esperado.put("id", LAGO_ID);
        esperado.put("nombreZona", "Zona lago menor");
        esperado.put("latitudCentro", 19.421);
        esperado.put("longitudCentro", -99.1825);
        esperado.put("radioMetros", 50.0);
        esperado.put("fechaRegistro", "2026-10-02T16:00:00Z");
        esperado.put("descripcion", "Dosel mixto de oyameles");
        esperado.put("ultimoAnalisis", Map.of("id", "e0000000-0000-0000-0000-000000000004",
                "resultado", "no_determinado", "fechaCaptura", "2026-10-07T09:00:00Z"));
        assertEquals(esperado, lambda.json(res));
    }

    @Test
    void detailKeepsNullColumns() {
        Map<String, Object> json = lambda.json(lambda.get("/zonas/" + CALZADA_ID, auth));
        assertEquals(true, json.containsKey("radioMetros"));
        assertEquals(null, json.get("radioMetros"));
        assertEquals(null, json.get("descripcion"));
    }

    @Test
    void detailUnknownIdIs404() {
        assertEquals(404, lambda.get("/zonas/c0000000-0000-0000-0000-0000000000ff", auth).getStatusCode());
    }

    @Test
    void detailInvalidIdIs400() {
        assertEquals(400, lambda.get("/zonas/no-es-uuid", auth).getStatusCode());
    }
}
