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

/** GET /arboles y GET /arboles/{id}: solo lectura, requieren JWT. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArbolesApiTest {

    private static final String OYAMEL_ID = "b0000000-0000-0000-0000-000000000001";
    private static final String AHUEHUETE_ID = "b0000000-0000-0000-0000-000000000002";

    private LambdaTestSupport lambda;
    private Map<String, String> auth;

    @BeforeAll
    void start() {
        lambda = new LambdaTestSupport();
        String token = (String) lambda.json(lambda.post("/auth/login",
                "{\"username\":\"demo@sheets.tt\",\"password\":\"Demo1234!\"}")).get("access_token");
        auth = Map.of("authorization", "Bearer " + token);
    }

    @AfterAll
    void stop() {
        lambda.close();
    }

    @Test
    void listRequiresToken() {
        assertEquals(401, lambda.get("/arboles", Map.of()).getStatusCode());
    }

    @Test
    void listReturnsTreesMostRecentFirst() {
        APIGatewayV2HTTPResponse res = lambda.get("/arboles", auth);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        List<Map<String, Object>> arboles = lambda.jsonList(res);
        assertEquals(List.of("b0000000-0000-0000-0000-000000000003", AHUEHUETE_ID, OYAMEL_ID),
                arboles.stream().map(a -> a.get("id")).toList());
    }

    @Test
    void detailReturnsContractPayload() {
        APIGatewayV2HTTPResponse res = lambda.get("/arboles/" + OYAMEL_ID, auth);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        Map<String, Object> esperado = new HashMap<>();
        esperado.put("id", OYAMEL_ID);
        esperado.put("nombreEspecifico", "Oyamel del lago");
        esperado.put("especie", "Abies religiosa");
        esperado.put("latitud", 19.4205);
        esperado.put("longitud", -99.1818);
        esperado.put("fechaRegistro", "2026-10-01T15:00:00Z");
        esperado.put("descripcion", "Junto al lago menor");
        esperado.put("ultimoAnalisis", Map.of("id", "e0000000-0000-0000-0000-000000000001",
                "resultado", "infestado", "fechaCaptura", "2026-10-03T15:10:00Z"));
        assertEquals(esperado, lambda.json(res));
    }

    @Test
    void detailKeepsNullDescription() {
        Map<String, Object> json = lambda.json(lambda.get("/arboles/" + AHUEHUETE_ID, auth));
        assertEquals(true, json.containsKey("descripcion"), "descripcion debe venir aunque sea null");
        assertEquals(null, json.get("descripcion"));
    }

    @Test
    void treeWithoutAnalysesHasNullLastAnalysis() {
        Map<String, Object> json = lambda.json(lambda.get("/arboles/b0000000-0000-0000-0000-000000000003", auth));
        assertEquals(true, json.containsKey("ultimoAnalisis"), json.toString());
        assertEquals(null, json.get("ultimoAnalisis"));
    }

    @Test
    void detailUnknownIdIs404() {
        assertEquals(404, lambda.get("/arboles/b0000000-0000-0000-0000-0000000000ff", auth).getStatusCode());
    }

    @Test
    void detailInvalidIdIs400() {
        assertEquals(400, lambda.get("/arboles/no-es-uuid", auth).getStatusCode());
    }
}
