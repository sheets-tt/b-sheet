package tt.sheets;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.core.type.Argument;
import io.micronaut.function.aws.proxy.payload2.APIGatewayV2HTTPEventFunction;
import io.micronaut.json.JsonMapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Invoca el handler real de Lambda (API Gateway HTTP API payload v2) con eventos sintéticos,
 * igual que lo haría AWS. Usa la BD local sheets_test (ver application-test.yml).
 */
final class LambdaTestSupport implements AutoCloseable {

    private final APIGatewayV2HTTPEventFunction handler;
    private final ApplicationContext context;

    LambdaTestSupport(Map<String, Object> extraProperties) {
        // Mismo builder que usa AWS (constructor sin args => entorno "function", init eager de
        // singletons); solo se agrega el entorno "test" y propiedades extra.
        this.handler = new APIGatewayV2HTTPEventFunction() {
            @Override
            protected ApplicationContextBuilder newApplicationContextBuilder() {
                return super.newApplicationContextBuilder()
                        .environments("test")
                        .properties(extraProperties);
            }
        };
        this.context = handler.getApplicationContext();
    }

    LambdaTestSupport() {
        this(Map.of());
    }

    APIGatewayV2HTTPResponse request(String method, String pathAndQuery, String body, Map<String, String> headers) {
        Map<String, String> h = new HashMap<>(headers);
        if (body != null) {
            h.putIfAbsent("content-type", "application/json");
        }
        // "/analisis?tipo=follaje" -> rawPath + rawQueryString + queryStringParameters, como API Gateway.
        int q = pathAndQuery.indexOf('?');
        String path = q < 0 ? pathAndQuery : pathAndQuery.substring(0, q);
        String query = q < 0 ? "" : pathAndQuery.substring(q + 1);
        Map<String, String> params = new HashMap<>();
        for (String pair : query.split("&")) {
            if (!pair.isEmpty()) {
                int eq = pair.indexOf('=');
                // Como API Gateway (HTTP API v2): los parámetros repetidos se unen con comas.
                params.merge(decode(eq < 0 ? pair : pair.substring(0, eq)), eq < 0 ? "" : decode(pair.substring(eq + 1)),
                        (a, b) -> a + "," + b);
            }
        }
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withVersion("2.0")
                .withRouteKey("ANY /{proxy+}")
                .withRawPath(path)
                .withRawQueryString(query)
                .withQueryStringParameters(params.isEmpty() ? null : params)
                .withHeaders(h)
                .withBody(body)
                .withIsBase64Encoded(false)
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder()
                                .withMethod(method)
                                .withPath(path)
                                .withProtocol("HTTP/1.1")
                                .withSourceIp("127.0.0.1")
                                .withUserAgent("junit")
                                .build())
                        .withStage("$default")
                        .withRequestId("test-request")
                        .build())
                .build();
        return handler.handleRequest(event, new StubContext());
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    APIGatewayV2HTTPResponse get(String path, Map<String, String> headers) {
        return request("GET", path, null, headers);
    }

    APIGatewayV2HTTPResponse post(String path, String body) {
        return request("POST", path, body, Map.of());
    }

    /** Cabecera Authorization con el JWT de una cuenta de db/demo (contraseña Demo1234!). */
    Map<String, String> bearer(String correo) {
        APIGatewayV2HTTPResponse res = post("/auth/login",
                "{\"username\":\"" + correo + "\",\"password\":\"Demo1234!\"}");
        if (res.getStatusCode() != 200) {
            throw new IllegalStateException("login de " + correo + " falló: " + body(res));
        }
        return Map.of("authorization", "Bearer " + json(res).get("access_token"));
    }

    /** Bean del contexto del handler (p. ej. JdbcOperations para limpiar lo que crea una prueba). */
    <T> T bean(Class<T> tipo) {
        return context.getBean(tipo);
    }

    static String body(APIGatewayV2HTTPResponse response) {
        String b = response.getBody();
        if (b != null && response.getIsBase64Encoded()) {
            return new String(Base64.getDecoder().decode(b), StandardCharsets.UTF_8);
        }
        return b;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> json(APIGatewayV2HTTPResponse response) {
        try {
            return context.getBean(JsonMapper.class).readValue(body(response), Map.class);
        } catch (IOException e) {
            throw new IllegalStateException("Respuesta no es JSON: " + body(response), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    List<Map<String, Object>> jsonList(APIGatewayV2HTTPResponse response) {
        try {
            return (List) context.getBean(JsonMapper.class).readValue(body(response), Argument.listOf(Map.class));
        } catch (IOException e) {
            throw new IllegalStateException("Respuesta no es un arreglo JSON: " + body(response), e);
        }
    }

    static String header(APIGatewayV2HTTPResponse response, String name) {
        if (response.getHeaders() != null) {
            for (var e : response.getHeaders().entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
            }
        }
        if (response.getMultiValueHeaders() != null) {
            for (var e : response.getMultiValueHeaders().entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) return String.join(",", e.getValue());
            }
        }
        return null;
    }

    @Override
    public void close() {
        handler.close();
    }

    private static final class StubContext implements Context {
        public String getAwsRequestId() { return "test-request"; }
        public String getLogGroupName() { return "test"; }
        public String getLogStreamName() { return "test"; }
        public String getFunctionName() { return "sheets-backend-test"; }
        public String getFunctionVersion() { return "$LATEST"; }
        public String getInvokedFunctionArn() { return "arn:aws:lambda:us-east-1:000000000000:function:sheets-backend-test"; }
        public CognitoIdentity getIdentity() { return null; }
        public ClientContext getClientContext() { return null; }
        public int getRemainingTimeInMillis() { return 20_000; }
        public int getMemoryLimitInMB() { return 1024; }
        public LambdaLogger getLogger() {
            return new LambdaLogger() {
                public void log(String message) { System.out.println(message); }
                public void log(byte[] message) { System.out.println(new String(message, StandardCharsets.UTF_8)); }
            };
        }
    }
}
