package tt.sheets;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthApiTest {

    private static final String DEMO_LOGIN = "{\"username\":\"demo@sheets.tt\",\"password\":\"Demo1234!\"}";

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
    void healthIsUp() {
        APIGatewayV2HTTPResponse res = lambda.get("/health", Map.of());
        assertEquals(200, res.getStatusCode());
        assertEquals(Map.of("status", "UP"), lambda.json(res));
    }

    @Test
    void loginOkReturnsContractPayload() {
        APIGatewayV2HTTPResponse res = lambda.post("/auth/login", DEMO_LOGIN);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        Map<String, Object> json = lambda.json(res);
        assertEquals(Map.of("access_token", json.get("access_token"), "token_type", "Bearer",
                "expires_in", 3600, "username", "demo@sheets.tt", "roles", List.of("USUARIO")), json);
        String token = (String) json.get("access_token");
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length, "debe ser un JWT");
    }

    @Test
    void loginIsCaseInsensitiveOnUsername() {
        APIGatewayV2HTTPResponse res = lambda.post("/auth/login",
                "{\"username\":\"Demo@Sheets.TT\",\"password\":\"Demo1234!\"}");
        assertEquals(200, res.getStatusCode());
        assertEquals("demo@sheets.tt", lambda.json(res).get("username"));
    }

    @Test
    void loginWrongPasswordIs401() {
        APIGatewayV2HTTPResponse res = lambda.post("/auth/login",
                "{\"username\":\"demo@sheets.tt\",\"password\":\"incorrecta\"}");
        assertEquals(401, res.getStatusCode());
        assertNotNull(lambda.json(res).get("message"));
    }

    @Test
    void loginUnknownUserIs401() {
        APIGatewayV2HTTPResponse res = lambda.post("/auth/login",
                "{\"username\":\"nadie@sheets.tt\",\"password\":\"Demo1234!\"}");
        assertEquals(401, res.getStatusCode());
    }

    @Test
    void loginBlankFieldsIs400() {
        assertEquals(400, lambda.post("/auth/login", "{\"username\":\"\",\"password\":\"\"}").getStatusCode());
        assertEquals(400, lambda.post("/auth/login", "{\"username\":\"demo@sheets.tt\"}").getStatusCode());
    }

    @Test
    void meWithTokenReturnsProfile() {
        String token = (String) lambda.json(lambda.post("/auth/login", DEMO_LOGIN)).get("access_token");
        APIGatewayV2HTTPResponse res = lambda.get("/me", Map.of("authorization", "Bearer " + token));
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        assertEquals(Map.of("username", "demo@sheets.tt", "displayName", "Usuario Demo"), lambda.json(res));
    }

    @Test
    void meWithoutTokenIs401() {
        assertEquals(401, lambda.get("/me", Map.of()).getStatusCode());
    }

    @Test
    void meWithInvalidTokenIs401() {
        assertEquals(401, lambda.get("/me", Map.of("authorization", "Bearer abc.def.ghi")).getStatusCode());
        // Firmado con otro secreto
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkZW1vQHNoZWV0cy50dCJ9."
                + "Zm9yZ2VkLXNpZ25hdHVyZS1mb3JnZWQtc2lnbmF0dXJl";
        assertEquals(401, lambda.get("/me", Map.of("authorization", "Bearer " + forged)).getStatusCode());
    }

    @Test
    void corsPreflightAllowsConfiguredOrigin() {
        APIGatewayV2HTTPResponse res = lambda.request("OPTIONS", "/auth/login", null, Map.of(
                "origin", "http://localhost:5173",
                "access-control-request-method", "POST",
                "access-control-request-headers", "content-type,authorization"));
        assertEquals(200, res.getStatusCode());
        assertEquals("http://localhost:5173", LambdaTestSupport.header(res, "Access-Control-Allow-Origin"));
    }

    @Test
    void corsRejectsUnknownOrigin() {
        APIGatewayV2HTTPResponse res = lambda.request("OPTIONS", "/auth/login", null, Map.of(
                "origin", "https://evil.example.com",
                "access-control-request-method", "POST"));
        String allow = LambdaTestSupport.header(res, "Access-Control-Allow-Origin");
        assertFalse("https://evil.example.com".equals(allow), "no debe permitir orígenes desconocidos");
        assertTrue(res.getStatusCode() >= 400 || allow == null);
    }
}
