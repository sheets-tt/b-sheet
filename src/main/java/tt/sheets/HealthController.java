package tt.sheets;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import java.util.Map;

/** GET /health — liveness simple, no toca la BD (barato en cada invocación Lambda). */
@Controller("/health")
@Secured(SecurityRule.IS_ANONYMOUS)
public class HealthController {

    @Get
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
