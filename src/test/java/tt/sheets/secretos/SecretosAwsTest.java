package tt.sheets.secretos;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * En AWS la contraseña de la BD y el secreto del JWT no viajan como variables de la Lambda (cualquiera
 * con lambda:GetFunctionConfiguration los lee): la Lambda recibe solo el nombre del secreto
 * (SECRETOS_ID) y los lee de Secrets Manager al arrancar.
 */
class SecretosAwsTest {

    private static final String JSON = "{\"DB_PASSWORD\":\"clave-bd\",\"JWT_SECRET\":\"" + "j".repeat(40) + "\"}";

    @Test
    void leeLasDosClavesDelJson() {
        assertEquals(Map.of("DB_PASSWORD", "clave-bd", "JWT_SECRET", "j".repeat(40)), SecretosAws.leer(JSON));
    }

    @Test
    void rechazaSecretosIncompletosOConClavesDesconocidas() {
        assertThrows(IllegalStateException.class, () -> SecretosAws.leer("{\"DB_PASSWORD\":\"x\"}"));
        assertThrows(IllegalStateException.class, () -> SecretosAws.leer("no es json"));
        // Solo estas dos claves: el secreto no puede sobrescribir otra configuración (p. ej. la URL de la BD).
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> SecretosAws.leer(
                "{\"DB_PASSWORD\":\"x\",\"JWT_SECRET\":\"y\",\"DB_URL\":\"jdbc:postgresql://evil\"}"));
        assertTrue(e.getMessage().contains("DB_URL"), e.getMessage());
    }

    @Test
    void sinSecretosIdNoLlamaASecretsManager() {
        List<String> pedidos = new ArrayList<>();
        ApplicationContextBuilder builder = ApplicationContext.builder();
        assertSame(builder, SecretosAws.agregar(builder, null, id -> { pedidos.add(id); return JSON; }));
        assertSame(builder, SecretosAws.agregar(builder, " ", id -> { pedidos.add(id); return JSON; }));
        assertEquals(List.of(), pedidos);
    }

    @Test
    void conSecretosIdLasPropiedadesSalenDeSecretsManager() {
        List<String> pedidos = new ArrayList<>();
        ApplicationContextBuilder builder = SecretosAws.agregar(ApplicationContext.builder().deduceEnvironment(false),
                "sheets-tt/b-sheet", id -> { pedidos.add(id); return JSON; });
        try (ApplicationContext ctx = builder.build()) {
            var env = ctx.getEnvironment().start();
            assertEquals(List.of("sheets-tt/b-sheet"), pedidos);
            // application.yml: password: ${DB_PASSWORD:} y secret: ${JWT_SECRET}
            assertEquals("clave-bd", env.getProperty("datasources.default.password", String.class).orElseThrow());
            assertEquals("j".repeat(40), env.getProperty(
                    "micronaut.security.token.jwt.signatures.secret.generator.secret", String.class).orElseThrow());
        }
    }

    @Test
    void laImagenDeLambdaUsaElHandlerQueLeeLosSecretos() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        assertTrue(dockerfile.contains("CMD [\"tt.sheets.LambdaHandler::handleRequest\"]"), dockerfile);
        assertEquals(tt.sheets.LambdaHandler.class, Class.forName("tt.sheets.LambdaHandler"));
    }
}
