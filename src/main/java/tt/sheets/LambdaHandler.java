package tt.sheets;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.function.aws.proxy.payload2.APIGatewayV2HTTPEventFunction;
import tt.sheets.secretos.SecretosAws;

/**
 * Handler de la Lambda (Dockerfile: CMD tt.sheets.LambdaHandler::handleRequest): el de Micronaut para
 * API Gateway HTTP API v2, más los secretos de Secrets Manager si la función define SECRETOS_ID.
 */
public class LambdaHandler extends APIGatewayV2HTTPEventFunction {

    public LambdaHandler() {
        super();
    }

    // Lo invoca el constructor del padre: solo usa métodos estáticos (los campos aún no existen).
    @Override
    protected ApplicationContextBuilder newApplicationContextBuilder() {
        return SecretosAws.agregar(super.newApplicationContextBuilder(), System.getenv("SECRETOS_ID"),
                SecretosAws::desdeSecretsManager);
    }
}
