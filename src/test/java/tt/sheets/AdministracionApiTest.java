package tt.sheets;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** GET /cuentas y GET /dispositivos: solo rol ADMINISTRADOR y sin datos sensibles. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdministracionApiTest {

    private LambdaTestSupport lambda;
    private Map<String, String> admin;
    private Map<String, String> experto;

    @BeforeAll
    void start() {
        lambda = new LambdaTestSupport();
        admin = lambda.bearer("admin@sheets.tt");
        experto = lambda.bearer("demo@sheets.tt");
    }

    @AfterAll
    void stop() {
        lambda.close();
    }

    @Test
    void requireToken() {
        assertEquals(401, lambda.get("/cuentas", Map.of()).getStatusCode());
        assertEquals(401, lambda.get("/dispositivos", Map.of()).getStatusCode());
    }

    @Test
    void fieldExpertIsForbidden() {
        assertEquals(403, lambda.get("/cuentas", experto).getStatusCode());
        assertEquals(403, lambda.get("/dispositivos", experto).getStatusCode());
    }

    @Test
    void adminListsAccountsWithoutPasswordHashes() {
        APIGatewayV2HTTPResponse res = lambda.get("/cuentas", admin);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        String body = LambdaTestSupport.body(res);
        assertFalse(body.contains("$2a$"), "no debe exponer hashes bcrypt: " + body);
        assertFalse(body.toLowerCase().contains("contrasena"), body);

        List<Map<String, Object>> cuentas = lambda.jsonList(res);
        assertEquals(Set.of("id", "correo", "nombreCompleto", "rol", "activa", "fechaCreacion"),
                cuentas.get(0).keySet());
        Map<String, Object> baja = cuentas.get(1);
        assertEquals("baja@sheets.tt", baja.get("correo"));
        assertEquals("EXPERTO_CAMPO", baja.get("rol"));
        assertEquals(false, baja.get("activa"));
    }

    @Test
    void adminListsDevicesWithoutPushToken() {
        APIGatewayV2HTTPResponse res = lambda.get("/dispositivos", admin);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        Map<String, Object> pixel = lambda.jsonList(res).get(0);
        assertEquals(Set.of("id", "identificadorUnico", "nombreDispositivo", "estado", "fechaRegistro",
                "cuentaVigente"), pixel.keySet());
        assertEquals("d0000000-0000-0000-0000-000000000001", pixel.get("id"));
        assertEquals("Activo", pixel.get("estado"));
        assertEquals("demo@sheets.tt", pixel.get("cuentaVigente"));
    }
}
