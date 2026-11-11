package tt.sheets;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * En producción la BD es la de TT_BKN (RDS) y el backend entra con un usuario de solo lectura:
 * Flyway no debe correr salvo que se pida explícitamente.
 */
class ProductionConfigTest {

    @Test
    void flywayIsDisabledByDefault() {
        // Sin entornos "dev" ni "test" (deduceEnvironment(false) evita que Micronaut detecte JUnit).
        // Solo se cargan las fuentes de configuración, sin crear beans (no hace falta BD ni JWT_SECRET).
        try (ApplicationContext context = ApplicationContext.builder().deduceEnvironment(false).build()) {
            Environment environment = context.getEnvironment().start();
            assertEquals(Optional.of(false),
                    environment.getProperty("flyway.datasources.default.enabled", Boolean.class));
        }
    }
}
