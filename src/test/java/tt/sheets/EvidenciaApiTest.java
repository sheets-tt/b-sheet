package tt.sheets;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GET /analisis/{id}/evidencia: URL de S3 firmada y temporal para ver la foto (el bucket es privado). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EvidenciaApiTest {

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

    private static String e(int n) {
        return "/analisis/e0000000-0000-0000-0000-00000000000" + n + "/evidencia";
    }

    @Test
    void requiresToken() {
        assertEquals(401, lambda.get(e(1), Map.of()).getStatusCode());
    }

    @Test
    void returnsShortLivedSignedUrlNotCached() {
        Instant antes = Instant.now();
        APIGatewayV2HTTPResponse res = lambda.get(e(1), auth);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        assertEquals("no-store", LambdaTestSupport.header(res, "Cache-Control"));

        Map<String, Object> json = lambda.json(res);
        URI url = URI.create((String) json.get("url"));
        // En us-east-1 el SDK firma contra el endpoint global (la CSP de f-sheet permite este host).
        assertEquals("descortezadores-archivos-prod.s3.amazonaws.com", url.getHost());
        assertEquals("/evidencias/e1.jpg", url.getPath());
        Instant expira = Instant.parse((String) json.get("expira"));
        assertTrue(!expira.isBefore(antes.plus(Duration.ofMinutes(5)).minusSeconds(5)) &&
                !expira.isAfter(Instant.now().plus(Duration.ofMinutes(5))), expira.toString());
    }

    @Test
    void acceptsTheFullS3UrlStoredByTtBkn() {
        APIGatewayV2HTTPResponse res = lambda.get(e(2), auth);
        assertEquals(200, res.getStatusCode(), LambdaTestSupport.body(res));
        assertEquals("/evidencias/e2.jpg", URI.create((String) lambda.json(res).get("url")).getPath());
    }

    @Test
    void analysisWithoutImageIs404WithMessage() {
        APIGatewayV2HTTPResponse res = lambda.get(e(5), auth);
        assertEquals(404, res.getStatusCode());
        assertEquals("Este análisis no tiene foto disponible.", lambda.json(res).get("message"));
    }

    @Test
    void unknownOrInvalidIds() {
        assertEquals(404, lambda.get("/analisis/e0000000-0000-0000-0000-0000000000ff/evidencia", auth).getStatusCode());
        assertEquals(400, lambda.get("/analisis/no-es-uuid/evidencia", auth).getStatusCode());
    }
}
