package tt.sheets.secretos;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;
import io.micronaut.context.ApplicationContextBuilder;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Secretos de b-sheet en AWS Secrets Manager. En la Lambda solo viaja SECRETOS_ID (el nombre del secreto,
 * no sensible); el secreto es un JSON {"DB_PASSWORD": "...", "JWT_SECRET": "..."} que se lee una vez al
 * arrancar y se agrega como fuente de propiedades: application.yml los usa vía ${DB_PASSWORD} y
 * ${JWT_SECRET}. Con SnapStart se leen en el init y quedan en el snapshot (cifrado por Lambda); al rotar
 * el secreto hay que publicar una versión nueva.
 */
public final class SecretosAws {

    /** Únicas claves aceptadas: el secreto no puede sobrescribir otra configuración. */
    static final Set<String> CLAVES = Set.of("DB_PASSWORD", "JWT_SECRET");

    private SecretosAws() {
    }

    public static Map<String, String> leer(String json) {
        Map<String, String> valores = new HashMap<>();
        try (JsonParser p = new JsonFactory().createParser(ObjectReadContext.empty(), json)) {
            if (p.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalStateException("El secreto debe ser un objeto JSON");
            }
            while (p.nextToken() == JsonToken.PROPERTY_NAME) {
                String clave = p.currentName();
                if (p.nextToken() != JsonToken.VALUE_STRING) {
                    throw new IllegalStateException("El valor de " + clave + " debe ser texto");
                }
                valores.put(clave, p.getText());
            }
        } catch (JacksonException e) {
            throw new IllegalStateException("El secreto no es JSON válido", e);
        }
        Set<String> sobrantes = new TreeSet<>(valores.keySet());
        sobrantes.removeAll(CLAVES);
        if (!sobrantes.isEmpty()) {
            throw new IllegalStateException("Claves no permitidas en el secreto: " + sobrantes);
        }
        if (!valores.keySet().containsAll(CLAVES)) {
            throw new IllegalStateException("El secreto debe traer " + new TreeSet<>(CLAVES));
        }
        return Map.copyOf(valores);
    }

    /** Sin {@code secretosId} (desarrollo, tests) no hace nada: se usan las variables de entorno. */
    public static ApplicationContextBuilder agregar(ApplicationContextBuilder builder, String secretosId,
                                                    Function<String, String> obtener) {
        if (secretosId == null || secretosId.isBlank()) {
            return builder;
        }
        // properties(): fuente del propio contexto, por encima de application.yml y de las variables de entorno.
        return builder.properties(new HashMap<>(leer(obtener.apply(secretosId.trim()))));
    }

    /** Lectura real (en la Lambda: credenciales del rol, región de AWS_REGION). */
    public static String desdeSecretsManager(String secretosId) {
        try (SecretsManagerClient cliente = SecretsManagerClient.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
                .build()) {
            return cliente.getSecretValue(r -> r.secretId(secretosId)).secretString();
        }
    }
}
