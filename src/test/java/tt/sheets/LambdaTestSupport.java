package tt.sheets;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.function.aws.proxy.payload2.APIGatewayV2HTTPEventFunction;
import io.micronaut.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
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

    APIGatewayV2HTTPResponse request(String method, String path, String body, Map<String, String> headers) {
        Map<String, String> h = new HashMap<>(headers);
        if (body != null) {
            h.putIfAbsent("content-type", "application/json");
        }
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withVersion("2.0")
                .withRouteKey("ANY /{proxy+}")
                .withRawPath(path)
                .withRawQueryString("")
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

    APIGatewayV2HTTPResponse get(String path, Map<String, String> headers) {
        return request("GET", path, null, headers);
    }

    APIGatewayV2HTTPResponse post(String path, String body) {
        return request("POST", path, body, Map.of());
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
