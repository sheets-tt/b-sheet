package tt.sheets;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * POST /cuentas, PUT /cuentas/{id}, PATCH /cuentas/{id}/desactivar y /activar (RN-01, RF-26, CU-A02):
 * solo ADMINISTRADOR, sin exponer hashes, y desactivar o cambiar el rol corta los tokens ya emitidos.
 * Las cuentas que se crean aquí usan el prefijo gestion- y se borran después de cada prueba.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GestionCuentasApiTest {

    private static final String CONTRASENA = "Secreta123";
    private static final String DEMO_ID = "a0000000-0000-0000-0000-000000000001";
    private static final String ADMIN_ID = "a0000000-0000-0000-0000-000000000002";
    private static final String NADIE_ID = "f0000000-0000-0000-0000-00000000000f";
    private static final Set<String> CLAVES = Set.of("id", "correo", "nombreCompleto", "rol", "activa", "fechaCreacion");

    private LambdaTestSupport lambda;
    private Map<String, String> admin;
    private Map<String, String> experto;

    @BeforeAll
    void start() {
        lambda = new LambdaTestSupport();
        limpiar();
        admin = lambda.bearer("admin@sheets.tt");
        experto = lambda.bearer("demo@sheets.tt");
    }

    @AfterEach
    void limpiar() {
        lambda.bean(JdbcOperations.class).execute(c ->
                c.prepareStatement("DELETE FROM cuenta WHERE correo LIKE 'gestion-%'").executeUpdate());
    }

    @AfterAll
    void stop() {
        lambda.close();
    }

    // ---- utilidades ----

    private static String cuerpo(String correo, String nombre, String rol, String contrasena) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"correo\":").append(texto(correo)).append(',');
        sb.append("\"nombreCompleto\":").append(texto(nombre)).append(',');
        sb.append("\"rol\":").append(texto(rol));
        if (contrasena != null) {
            sb.append(",\"contrasena\":").append(texto(contrasena));
        }
        return sb.append('}').toString();
    }

    private static String texto(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private APIGatewayV2HTTPResponse post(String body, Map<String, String> auth) {
        return lambda.request("POST", "/cuentas", body, auth);
    }

    private APIGatewayV2HTTPResponse put(String id, String body, Map<String, String> auth) {
        return lambda.request("PUT", "/cuentas/" + id, body, auth);
    }

    private APIGatewayV2HTTPResponse patch(String id, String accion, Map<String, String> auth) {
        return lambda.request("PATCH", "/cuentas/" + id + "/" + accion, null, auth);
    }

    private Map<String, Object> crear(String correo, String rol) {
        APIGatewayV2HTTPResponse res = post(cuerpo(correo, "Persona de prueba", rol, CONTRASENA), admin);
        assertEquals(201, res.getStatusCode(), LambdaTestSupport.body(res));
        return lambda.json(res);
    }

    private APIGatewayV2HTTPResponse login(String correo, String contrasena) {
        return lambda.post("/auth/login", "{\"username\":\"" + correo + "\",\"password\":\"" + contrasena + "\"}");
    }

    private Map<String, String> token(String correo, String contrasena) {
        APIGatewayV2HTTPResponse res = login(correo, contrasena);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        return Map.of("authorization", "Bearer " + lambda.json(res).get("access_token"));
    }

    private void assertSinSecretos(APIGatewayV2HTTPResponse res) {
        String body = String.valueOf(LambdaTestSupport.body(res));
        assertFalse(body.contains("$2a$"), "no debe exponer hashes: " + body);
        assertFalse(body.toLowerCase().contains("contrasena") || body.contains(CONTRASENA), body);
    }

    private void assertError(int status, APIGatewayV2HTTPResponse res) {
        assertEquals(status, res.getStatusCode(), LambdaTestSupport.body(res));
        Map<String, Object> json = lambda.json(res);
        assertEquals(Set.of("message"), json.keySet(), LambdaTestSupport.body(res));
        assertInstanceOf(String.class, json.get("message"));
    }

    // ---- seguridad ----

    @Test
    void sinTokenEs401() {
        assertEquals(401, post(cuerpo("gestion-x@sheets.tt", "X", "EXPERTO_CAMPO", CONTRASENA), Map.of()).getStatusCode());
        assertEquals(401, put(DEMO_ID, cuerpo("demo@sheets.tt", "X", "EXPERTO_CAMPO", null), Map.of()).getStatusCode());
        assertEquals(401, patch(DEMO_ID, "desactivar", Map.of()).getStatusCode());
        assertEquals(401, patch(DEMO_ID, "activar", Map.of()).getStatusCode());
    }

    @Test
    void unExpertoDeCampoNoGestionaCuentas() {
        assertEquals(403, post(cuerpo("gestion-x@sheets.tt", "X", "ADMINISTRADOR", CONTRASENA), experto).getStatusCode());
        assertEquals(403, put(ADMIN_ID, cuerpo("admin@sheets.tt", "X", "EXPERTO_CAMPO", null), experto).getStatusCode());
        assertEquals(403, patch(ADMIN_ID, "desactivar", experto).getStatusCode());
        assertEquals(403, patch(ADMIN_ID, "activar", experto).getStatusCode());
        assertEquals(401, login("gestion-x@sheets.tt", CONTRASENA).getStatusCode(), "no se creó nada");
    }

    // ---- POST /cuentas ----

    @Test
    void creaUnaCuentaQuePuedeIniciarSesion() {
        APIGatewayV2HTTPResponse res = post(cuerpo("  Gestion-Nueva@Sheets.TT ", "  Nueva Persona  ", "EXPERTO_CAMPO",
                CONTRASENA), admin);
        assertEquals(201, res.getStatusCode(), LambdaTestSupport.body(res));
        assertSinSecretos(res);
        Map<String, Object> json = lambda.json(res);
        assertEquals(CLAVES, json.keySet());
        assertEquals("gestion-nueva@sheets.tt", json.get("correo"));
        assertEquals("Nueva Persona", json.get("nombreCompleto"));
        assertEquals("EXPERTO_CAMPO", json.get("rol"));
        assertEquals(true, json.get("activa"));
        assertNotNull(json.get("fechaCreacion"));

        APIGatewayV2HTTPResponse sesion = login("gestion-nueva@sheets.tt", CONTRASENA);
        assertEquals(200, sesion.getStatusCode(), LambdaTestSupport.body(sesion));
        assertEquals(List.of("EXPERTO_CAMPO"), lambda.json(sesion).get("roles"));

        // Aparece en el listado.
        assertTrue(lambda.jsonList(lambda.get("/cuentas", admin)).stream()
                .anyMatch(c -> json.get("id").equals(c.get("id"))));
    }

    @Test
    void creaUnAdministradorConPermisosDeAdministrador() {
        crear("gestion-admin@sheets.tt", "ADMINISTRADOR");
        Map<String, String> nuevo = token("gestion-admin@sheets.tt", CONTRASENA);
        assertEquals(200, lambda.get("/cuentas", nuevo).getStatusCode());
    }

    @Test
    void camposDeMasNoFijanEstadoNiHashNiId() {
        String body = "{\"correo\":\"gestion-colada@sheets.tt\",\"nombreCompleto\":\"Colada\",\"rol\":\"EXPERTO_CAMPO\","
                + "\"contrasena\":\"" + CONTRASENA + "\",\"activa\":false,\"estadoCuenta\":\"desactivada\","
                + "\"estado_cuenta\":\"desactivada\",\"contrasenaHash\":\"$2a$10$falso\",\"id\":\"" + DEMO_ID + "\"}";
        // Los campos desconocidos se ignoran: nace activa, con id propio y el hash de su contraseña.
        APIGatewayV2HTTPResponse res = post(body, admin);
        assertEquals(201, res.getStatusCode(), LambdaTestSupport.body(res));
        Map<String, Object> json = lambda.json(res);
        assertEquals(true, json.get("activa"));
        assertNotEquals(DEMO_ID, json.get("id"));
        assertEquals(200, login("gestion-colada@sheets.tt", CONTRASENA).getStatusCode());
    }

    @Test
    void correoRepetidoEs409SinImportarMayusculas() {
        assertError(409, post(cuerpo("demo@sheets.tt", "Otra", "EXPERTO_CAMPO", CONTRASENA), admin));
        assertError(409, post(cuerpo("DEMO@Sheets.tt", "Otra", "EXPERTO_CAMPO", CONTRASENA), admin));
        crear("gestion-dup@sheets.tt", "EXPERTO_CAMPO");
        assertError(409, post(cuerpo("Gestion-Dup@sheets.tt", "Otra", "ADMINISTRADOR", CONTRASENA), admin));
    }

    @Test
    void datosInvalidosSon400() {
        List<String> invalidos = List.of(
                cuerpo("no-es-correo", "X", "EXPERTO_CAMPO", CONTRASENA),
                cuerpo("", "X", "EXPERTO_CAMPO", CONTRASENA),
                cuerpo(null, "X", "EXPERTO_CAMPO", CONTRASENA),
                cuerpo("gestion-" + "a".repeat(56) + "@" + "b".repeat(60) + "." + "c".repeat(60) + "." + "d".repeat(60)
                        + ".sheetss", "X", "EXPERTO_CAMPO", CONTRASENA), // 255 caracteres
                cuerpo("gestion-x@sheets.tt", "   ", "EXPERTO_CAMPO", CONTRASENA),
                cuerpo("gestion-x@sheets.tt", null, "EXPERTO_CAMPO", CONTRASENA),
                cuerpo("gestion-x@sheets.tt", "n".repeat(256), "EXPERTO_CAMPO", CONTRASENA),
                cuerpo("gestion-x@sheets.tt", "X", "USUARIO", CONTRASENA),
                cuerpo("gestion-x@sheets.tt", "X", "administrador", CONTRASENA),
                cuerpo("gestion-x@sheets.tt", "X", "Administrador", CONTRASENA),
                cuerpo("gestion-x@sheets.tt", "X", null, CONTRASENA),
                cuerpo("gestion-x@sheets.tt", "X", "EXPERTO_CAMPO", null),
                cuerpo("gestion-x@sheets.tt", "X", "EXPERTO_CAMPO", ""),
                cuerpo("gestion-x@sheets.tt", "X", "EXPERTO_CAMPO", "        "),
                cuerpo("gestion-x@sheets.tt", "X", "EXPERTO_CAMPO", "Corta12"),
                cuerpo("gestion-x@sheets.tt", "X", "EXPERTO_CAMPO", "ñ".repeat(37))); // 74 bytes > 72
        for (String body : invalidos) {
            APIGatewayV2HTTPResponse res = post(body, admin);
            assertError(400, res);
            assertSinSecretos(res);
        }
        assertEquals(400, post("{\"correo\":", admin).getStatusCode());
        assertEquals(401, login("gestion-x@sheets.tt", CONTRASENA).getStatusCode(), "no se creó nada");
    }

    @Test
    void aceptaLosLimites() {
        // 254 en total; la parte local llega a su máximo de 64 (RFC 5321) y cada etiqueta del dominio a 60.
        String correo254 = "gestion-" + "a".repeat(56) + "@" + "b".repeat(60) + "." + "c".repeat(60) + "."
                + "d".repeat(60) + ".sheets";
        assertEquals(254, correo254.length());
        APIGatewayV2HTTPResponse res = post(cuerpo(correo254, "n".repeat(255), "EXPERTO_CAMPO", "ñ".repeat(36)), admin);
        assertEquals(201, res.getStatusCode(), LambdaTestSupport.body(res));
        assertEquals(200, login(correo254, "ñ".repeat(36)).getStatusCode());
        assertEquals(201, post(cuerpo("gestion-ocho@sheets.tt", "Ocho", "EXPERTO_CAMPO", "12345678"), admin).getStatusCode());
    }

    // ---- PUT /cuentas/{id} ----

    @Test
    void editaDatosYRolSinCambiarLaContrasena() {
        String id = (String) crear("gestion-editar@sheets.tt", "EXPERTO_CAMPO").get("id");
        APIGatewayV2HTTPResponse res = put(id, cuerpo("Gestion-Editada@sheets.tt", " Nombre Nuevo ", "ADMINISTRADOR", null), admin);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        assertSinSecretos(res);
        Map<String, Object> json = lambda.json(res);
        assertEquals(Map.of("id", id, "correo", "gestion-editada@sheets.tt", "nombreCompleto", "Nombre Nuevo",
                "rol", "ADMINISTRADOR", "activa", true, "fechaCreacion", json.get("fechaCreacion")), json);

        // Contraseña en blanco también conserva la actual.
        assertEquals(200, put(id, cuerpo("gestion-editada@sheets.tt", "Nombre Nuevo", "ADMINISTRADOR", ""), admin).getStatusCode());
        APIGatewayV2HTTPResponse sesion = login("gestion-editada@sheets.tt", CONTRASENA);
        assertEquals(200, sesion.getStatusCode());
        assertEquals(List.of("ADMINISTRADOR"), lambda.json(sesion).get("roles"));
        assertEquals(401, login("gestion-editar@sheets.tt", CONTRASENA).getStatusCode(), "el correo viejo ya no existe");
    }

    @Test
    void cambiarLaContrasenaInvalidaLaAnterior() {
        String id = (String) crear("gestion-clave@sheets.tt", "EXPERTO_CAMPO").get("id");
        assertEquals(200, put(id, cuerpo("gestion-clave@sheets.tt", "Clave", "EXPERTO_CAMPO", "OtraClave456"), admin).getStatusCode());
        assertEquals(401, login("gestion-clave@sheets.tt", CONTRASENA).getStatusCode());
        assertEquals(200, login("gestion-clave@sheets.tt", "OtraClave456").getStatusCode());
    }

    @Test
    void editarValidaComoAlCrear() {
        String id = (String) crear("gestion-valida@sheets.tt", "EXPERTO_CAMPO").get("id");
        assertError(400, put(id, cuerpo("no-es-correo", "X", "EXPERTO_CAMPO", null), admin));
        assertError(400, put(id, cuerpo("gestion-valida@sheets.tt", " ", "EXPERTO_CAMPO", null), admin));
        assertError(400, put(id, cuerpo("gestion-valida@sheets.tt", "X", "JEFE", null), admin));
        assertError(400, put(id, cuerpo("gestion-valida@sheets.tt", "X", "EXPERTO_CAMPO", "Corta12"), admin));
        assertError(400, put(id, cuerpo("gestion-valida@sheets.tt", "X", "EXPERTO_CAMPO", "ñ".repeat(37)), admin));
        assertEquals(400, put("no-es-uuid", cuerpo("gestion-valida@sheets.tt", "X", "EXPERTO_CAMPO", null), admin).getStatusCode());
        assertEquals(200, login("gestion-valida@sheets.tt", CONTRASENA).getStatusCode(), "sigue igual");
    }

    @Test
    void editarConElCorreoDeOtraCuentaEs409() {
        String id = (String) crear("gestion-choque@sheets.tt", "EXPERTO_CAMPO").get("id");
        assertError(409, put(id, cuerpo("Demo@Sheets.tt", "X", "EXPERTO_CAMPO", null), admin));
        // Su propio correo con otras mayúsculas no choca consigo misma.
        assertEquals(200, put(id, cuerpo("GESTION-CHOQUE@sheets.tt", "X", "EXPERTO_CAMPO", null), admin).getStatusCode());
    }

    @Test
    void idInexistenteEs404() {
        assertError(404, put(NADIE_ID, cuerpo("gestion-nadie@sheets.tt", "X", "EXPERTO_CAMPO", null), admin));
        assertError(404, patch(NADIE_ID, "desactivar", admin));
        assertError(404, patch(NADIE_ID, "activar", admin));
        assertEquals(400, patch("no-es-uuid", "desactivar", admin).getStatusCode());
    }

    // ---- el administrador no se deja sin acceso ----

    @Test
    void unAdministradorNoPuedeDesactivarseNiQuitarseElRol() {
        String id = (String) crear("gestion-jefa@sheets.tt", "ADMINISTRADOR").get("id");
        Map<String, String> jefa = token("gestion-jefa@sheets.tt", CONTRASENA);

        assertError(409, patch(id, "desactivar", jefa));
        assertError(409, put(id, cuerpo("gestion-jefa@sheets.tt", "Jefa", "EXPERTO_CAMPO", null), jefa));
        assertError(409, put(id, cuerpo("GESTION-JEFA@sheets.tt", "Jefa", "EXPERTO_CAMPO", null), jefa));
        // Sigue activa y administradora; sí puede editar su nombre.
        assertEquals(200, lambda.get("/cuentas", jefa).getStatusCode());
        APIGatewayV2HTTPResponse res = put(id, cuerpo("gestion-jefa@sheets.tt", "Jefa Renombrada", "ADMINISTRADOR", null), jefa);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        assertEquals(200, lambda.get("/cuentas", jefa).getStatusCode());
        // Otro administrador sí puede desactivarla.
        assertEquals(false, lambda.json(patch(id, "desactivar", admin)).get("activa"));
    }

    @Test
    void losMensajesDe409DistinguenElCorreoDeLaCuentaPropia() {
        // La web muestra en el campo Correo solo los 409 cuyo mensaje menciona "correo".
        String id = (String) crear("gestion-mensajes@sheets.tt", "ADMINISTRADOR").get("id");
        Map<String, String> propia = token("gestion-mensajes@sheets.tt", CONTRASENA);

        String duplicadoAlta = mensaje(post(cuerpo("DEMO@sheets.tt", "X", "EXPERTO_CAMPO", CONTRASENA), admin));
        String duplicadoEdicion = mensaje(put(id, cuerpo("demo@sheets.tt", "X", "ADMINISTRADOR", null), admin));
        assertEquals("Ya existe una cuenta con ese correo", duplicadoAlta);
        assertEquals("Ya existe una cuenta con ese correo", duplicadoEdicion);

        String desactivarse = mensaje(patch(id, "desactivar", propia));
        String quitarseRol = mensaje(put(id, cuerpo("gestion-mensajes@sheets.tt", "X", "EXPERTO_CAMPO", null), propia));
        assertEquals("No puedes desactivar tu propia cuenta", desactivarse);
        assertEquals("No puedes quitarte el rol de administrador", quitarseRol);
        assertFalse(desactivarse.toLowerCase().contains("correo"), desactivarse);
        assertFalse(quitarseRol.toLowerCase().contains("correo"), quitarseRol);
    }

    private String mensaje(APIGatewayV2HTTPResponse res) {
        assertError(409, res);
        return (String) lambda.json(res).get("message");
    }

    // ---- PATCH desactivar / activar ----

    @Test
    void desactivarCortaElLoginYLosTokensYaEmitidos() {
        String id = (String) crear("gestion-baja@sheets.tt", "EXPERTO_CAMPO").get("id");
        Map<String, String> suToken = token("gestion-baja@sheets.tt", CONTRASENA);
        assertEquals(200, lambda.get("/me", suToken).getStatusCode());
        assertEquals(200, lambda.get("/arboles", suToken).getStatusCode());

        APIGatewayV2HTTPResponse res = patch(id, "desactivar", admin);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        assertSinSecretos(res);
        assertEquals(CLAVES, lambda.json(res).keySet());
        assertEquals(false, lambda.json(res).get("activa"));
        assertEquals(200, patch(id, "desactivar", admin).getStatusCode(), "idempotente");

        assertEquals(401, login("gestion-baja@sheets.tt", CONTRASENA).getStatusCode());
        assertEquals(401, lambda.get("/me", suToken).getStatusCode());
        assertEquals(401, lambda.get("/arboles", suToken).getStatusCode());
        assertEquals(401, lambda.get("/estadisticas", suToken).getStatusCode());

        APIGatewayV2HTTPResponse alta = patch(id, "activar", admin);
        assertEquals(200, alta.getStatusCode(), LambdaTestSupport.body(alta));
        assertEquals(true, lambda.json(alta).get("activa"));
        assertEquals(200, login("gestion-baja@sheets.tt", CONTRASENA).getStatusCode());
    }

    @Test
    void reactivaUnaCuentaDeTtBknDesactivada() {
        String baja = "a0000000-0000-0000-0000-000000000003";
        try {
            assertEquals(true, lambda.json(patch(baja, "activar", admin)).get("activa"));
            assertEquals(200, login("baja@sheets.tt", "Demo1234!").getStatusCode());
        } finally {
            assertEquals(false, lambda.json(patch(baja, "desactivar", admin)).get("activa")); // deja db/demo como estaba
        }
        assertEquals(401, login("baja@sheets.tt", "Demo1234!").getStatusCode());
    }

    @Test
    void quitarElRolCortaLosPermisosDeAdministradorDeSusTokens() {
        String id = (String) crear("gestion-degradado@sheets.tt", "ADMINISTRADOR").get("id");
        Map<String, String> suToken = token("gestion-degradado@sheets.tt", CONTRASENA);
        assertEquals(200, lambda.get("/cuentas", suToken).getStatusCode());

        assertEquals(200, put(id, cuerpo("gestion-degradado@sheets.tt", "Degradado", "EXPERTO_CAMPO", null), admin).getStatusCode());
        assertEquals(401, lambda.get("/cuentas", suToken).getStatusCode(), "su token dice ADMINISTRADOR, la BD ya no");
        assertEquals(401, post(cuerpo("gestion-x@sheets.tt", "X", "ADMINISTRADOR", CONTRASENA), suToken).getStatusCode());

        Map<String, String> nuevo = token("gestion-degradado@sheets.tt", CONTRASENA);
        assertEquals(403, lambda.get("/cuentas", nuevo).getStatusCode());
        assertEquals(200, lambda.get("/arboles", nuevo).getStatusCode());
    }

    @Test
    void unTokenViejoNoValePorLaCuentaQueDespuesTomaSuCorreo() {
        // QA B1: el token se validaba solo por correo. Si la cuenta cambia de correo y otra lo toma después, el
        // token viejo no debe autenticar como la cuenta nueva.
        for (String rol : List.of("ADMINISTRADOR", "EXPERTO_CAMPO")) {
            String correo = "gestion-reuso-" + rol.toLowerCase() + "@sheets.tt";
            String id = (String) crear(correo, rol).get("id");
            Map<String, String> viejo = token(correo, CONTRASENA);
            assertEquals(200, lambda.get("/me", viejo).getStatusCode());

            assertEquals(200, put(id, cuerpo("gestion-reuso-renombrada@sheets.tt", "Antes", rol, null), admin).getStatusCode());
            assertEquals(401, lambda.get("/me", viejo).getStatusCode(), "cambió el correo de su cuenta");

            String otra = (String) crear(correo, rol).get("id");
            assertNotEquals(id, otra);
            assertEquals(401, lambda.get("/me", viejo).getStatusCode(), "el correo ahora es de otra cuenta (" + rol + ")");
            assertEquals(401, lambda.get("/arboles", viejo).getStatusCode());
            assertEquals(401, lambda.get("/cuentas", viejo).getStatusCode());
            // La cuenta nueva entra con su propio token.
            assertEquals(200, lambda.get("/me", token(correo, CONTRASENA)).getStatusCode());
            limpiar();
        }
    }

    @Test
    void elLoginEmiteElIdDeLaCuenta() {
        APIGatewayV2HTTPResponse res = login("demo@sheets.tt", "Demo1234!");
        String jwt = (String) lambda.json(res).get("access_token");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(payload.contains("\"cid\":\"" + DEMO_ID + "\""), payload);
        assertFalse(payload.contains("$2a$"), payload);
    }

    @Test
    void unTokenViejoNoImpideIniciarSesion() {
        String id = (String) crear("gestion-relogin@sheets.tt", "EXPERTO_CAMPO").get("id");
        Map<String, String> viejo = token("gestion-relogin@sheets.tt", CONTRASENA);
        put(id, cuerpo("gestion-relogin@sheets.tt", "X", "ADMINISTRADOR", null), admin);
        // La web puede reenviar el token viejo al login: no estorba.
        APIGatewayV2HTTPResponse res = lambda.request("POST", "/auth/login",
                "{\"username\":\"gestion-relogin@sheets.tt\",\"password\":\"" + CONTRASENA + "\"}", viejo);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        assertEquals(List.of("ADMINISTRADOR"), lambda.json(res).get("roles"));
    }

    @Test
    void corsPermitePutYPatchDesdeLaWeb() {
        for (String metodo : List.of("PUT", "PATCH")) {
            String ruta = "/cuentas/" + DEMO_ID + ("PATCH".equals(metodo) ? "/desactivar" : "");
            APIGatewayV2HTTPResponse res = lambda.request("OPTIONS", ruta, null, Map.of(
                    "origin", "http://localhost:5173",
                    "access-control-request-method", metodo,
                    "access-control-request-headers", "content-type,authorization"));
            assertEquals(200, res.getStatusCode(), metodo);
            assertEquals("http://localhost:5173", LambdaTestSupport.header(res, "Access-Control-Allow-Origin"));
            assertTrue(String.valueOf(LambdaTestSupport.header(res, "Access-Control-Allow-Methods")).contains(metodo),
                    String.valueOf(LambdaTestSupport.header(res, "Access-Control-Allow-Methods")));
        }
    }
}
