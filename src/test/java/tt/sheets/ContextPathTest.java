package tt.sheets;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** CONTEXT_PATH=/api permite que CloudFront reenvíe /api/* sin reescribir la ruta. */
class ContextPathTest {

    @Test
    void routesAreServedUnderContextPath() {
        try (LambdaTestSupport lambda = new LambdaTestSupport(Map.of("micronaut.server.context-path", "/api"))) {
            assertEquals(200, lambda.get("/api/health", Map.of()).getStatusCode());
            assertEquals(200, lambda.post("/api/auth/login",
                    "{\"username\":\"demo@sheets.tt\",\"password\":\"Demo1234!\"}").getStatusCode());
        }
    }
}
