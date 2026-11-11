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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** GET /analisis (filtros por query string) y GET /analisis/{id}: solo lectura, requieren JWT. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalisisApiTest {

    private static final String E1 = "e0000000-0000-0000-0000-000000000001";
    private static final String E3 = "e0000000-0000-0000-0000-000000000003";
    private static final String E5 = "e0000000-0000-0000-0000-000000000005";

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

    private List<Object> ids(String pathAndQuery) {
        APIGatewayV2HTTPResponse res = lambda.get(pathAndQuery, auth);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        return lambda.jsonList(res).stream().map(a -> a.get("id")).toList();
    }

    @Test
    void listRequiresToken() {
        assertEquals(401, lambda.get("/analisis", Map.of()).getStatusCode());
    }

    @Test
    void listWithoutFiltersReturnsEverythingMostRecentFirst() {
        assertEquals(5, ids("/analisis").size());
        assertEquals(E5, ids("/analisis").get(0));
    }

    @Test
    void listAppliesQueryFilters() {
        assertEquals(List.of(E5), ids("/analisis?tipo=follaje&resultado=infestado"));
        assertEquals(List.of(E1), ids("/analisis?arbol=b0000000-0000-0000-0000-000000000001"));
        assertEquals(2, ids("/analisis?zona=c0000000-0000-0000-0000-000000000001").size());
        assertEquals(1, ids("/analisis?estado=pendiente").size());
        assertEquals(3, ids("/analisis?desde=2026-10-04T12:00:00Z&hasta=2026-10-07T09:00:00Z").size());
        assertEquals(2, ids("/analisis?limite=2").size());
    }

    @Test
    void detailReturnsContractPayloadWithChromatic() {
        APIGatewayV2HTTPResponse res = lambda.get("/analisis/" + E3, auth);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        Map<String, Object> esperado = new HashMap<>();
        esperado.put("id", E3);
        esperado.put("tieneEvidencia", true); // la ruta interna de S3 no se expone
        esperado.put("latitudCaptura", 19.421);
        esperado.put("longitudCaptura", -99.1825);
        esperado.put("fechaCaptura", "2026-10-04T12:00:00Z");
        esperado.put("fechaSincronizacion", "2026-10-04T12:30:00Z");
        esperado.put("estadoSincronizacion", "sincronizado");
        esperado.put("tipoImagen", "follaje");
        esperado.put("resultadoClasificacion", "sano");
        esperado.put("confianza", 0.95);
        esperado.put("observaciones", null);
        esperado.put("idArbol", null);
        esperado.put("idZona", "c0000000-0000-0000-0000-000000000001");
        esperado.put("cromatico", Map.of("pctSano", 80.0, "pctRegular", 10.0, "pctDanado", 5.0, "pctIncierto", 5.0));
        assertEquals(esperado, lambda.json(res));
    }

    @Test
    void detailWithoutChromaticSendsNull() {
        Map<String, Object> json = lambda.json(lambda.get("/analisis/" + E5, auth));
        assertEquals(false, json.get("tieneEvidencia"));
        assertEquals(true, json.containsKey("cromatico"));
        assertEquals(null, json.get("cromatico"));
    }

    @Test
    void invalidEnumFilterIs400WithMessage() {
        APIGatewayV2HTTPResponse res = lambda.get("/analisis?tipo=raiz", auth);
        assertEquals(400, res.getStatusCode());
        assertEquals("tipo inválido: raiz (valores: corteza, follaje)", lambda.json(res).get("message"));
        assertEquals(400, lambda.get("/analisis?resultado=SANO", auth).getStatusCode());
        assertEquals(400, lambda.get("/analisis?estado=x", auth).getStatusCode());
    }

    @Test
    void invalidRangesAndLimitsAre400() {
        APIGatewayV2HTTPResponse res = lambda.get("/analisis?desde=2026-10-09T00:00:00Z&hasta=2026-10-01T00:00:00Z", auth);
        assertEquals(400, res.getStatusCode());
        assertNotNull(lambda.json(res).get("message"));
        assertEquals(400, lambda.get("/analisis?limite=0", auth).getStatusCode());
        assertEquals(400, lambda.get("/analisis?limite=501", auth).getStatusCode());
        assertEquals(400, lambda.get("/analisis?desde=ayer", auth).getStatusCode());
        assertEquals(400, lambda.get("/analisis?arbol=no-es-uuid", auth).getStatusCode());
    }

    @Test
    void detailUnknownIdIs404AndInvalidIdIs400() {
        assertEquals(404, lambda.get("/analisis/e0000000-0000-0000-0000-0000000000ff", auth).getStatusCode());
        assertEquals(400, lambda.get("/analisis/no-es-uuid", auth).getStatusCode());
    }
}
