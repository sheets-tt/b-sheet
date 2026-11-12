package tt.sheets;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tokens fabricados o alterados: solo pasa un JWT firmado con el secreto del servidor, vigente.
 * El primer caso (token propio, bien firmado) prueba que los tokens de estas pruebas se fabrican
 * bien; así los rechazos de los demás se deben a lo que cada uno altera, no a un token mal hecho.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TokenManipuladoApiTest {

    /** El de src/test/resources/application-test.yml. */
    private static final String SECRETO = "test-only-secret-0123456789abcdefghijklmnop";
    private static final String OTRO_SECRETO = "otro-secreto-que-no-es-del-servidor-0123456789";
    private static final String EXPERTO = "demo@sheets.tt";

    private LambdaTestSupport lambda;

    @BeforeAll
    void start() {
        lambda = new LambdaTestSupport();
    }

    @AfterAll
    void stop() {
        lambda.close();
    }

    private static String firmado(String secreto, String correo, List<String> roles, Instant expira) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(correo)
                .claim("roles", roles)
                .issueTime(Date.from(Instant.now().minusSeconds(60)))
                .notBeforeTime(Date.from(Instant.now().minusSeconds(60)))
                .expirationTime(Date.from(expira))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secreto.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private int status(String ruta, String token) {
        return lambda.get(ruta, Map.of("authorization", "Bearer " + token)).getStatusCode();
    }

    @Test
    void unTokenBienFirmadoYVigentePasa() throws Exception {
        String token = firmado(SECRETO, EXPERTO, List.of("EXPERTO_CAMPO"), Instant.now().plusSeconds(600));
        assertEquals(200, status("/me", token));
    }

    @Test
    void unTokenVencidoNoPasa() throws Exception {
        String token = firmado(SECRETO, EXPERTO, List.of("EXPERTO_CAMPO"), Instant.now().minusSeconds(5));
        assertEquals(401, status("/me", token));
    }

    @Test
    void unTokenFirmadoConOtraClaveNoPasa() throws Exception {
        String token = firmado(OTRO_SECRETO, EXPERTO, List.of("ADMINISTRADOR"), Instant.now().plusSeconds(600));
        assertEquals(401, status("/me", token));
        assertEquals(401, status("/cuentas", token));
    }

    @Test
    void unTokenSinFirmaConAlgNoneNoPasa() {
        long exp = Instant.now().plusSeconds(600).getEpochSecond();
        String token = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "."
                + b64("{\"sub\":\"" + EXPERTO + "\",\"roles\":[\"ADMINISTRADOR\"],\"exp\":" + exp + "}") + ".";
        assertEquals(401, status("/me", token));
        assertEquals(401, status("/cuentas", token));
    }

    @Test
    void cambiarElRolDentroDeUnTokenRealNoDaPermisosDeAdministrador() {
        // Token real de un experto; se reemplaza su payload por uno con rol ADMINISTRADOR y se
        // conserva la firma original.
        String real = lambda.bearer(EXPERTO).get("authorization").substring("Bearer ".length());
        String[] partes = real.split("\\.");
        long exp = Instant.now().plusSeconds(600).getEpochSecond();
        String alterado = partes[0] + "."
                + b64("{\"sub\":\"" + EXPERTO + "\",\"roles\":[\"ADMINISTRADOR\"],\"exp\":" + exp + "}") + "."
                + partes[2];
        assertEquals(401, status("/cuentas", alterado));
        // El original sigue siendo de un experto: 403, no 200.
        assertEquals(403, status("/cuentas", real));
    }

    @Test
    void basuraEnLugarDeTokenNoPasa() {
        assertEquals(401, status("/me", "no-es-un-jwt"));
        assertEquals(401, status("/me", ""));
        assertEquals(401, lambda.get("/me", Map.of("authorization", "Basic ZGVtbzpEZW1vMTIzNCE=")).getStatusCode());
    }
}
