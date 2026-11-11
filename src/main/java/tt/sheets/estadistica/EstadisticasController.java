package tt.sheets.estadistica;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import tt.sheets.estadistica.application.ConsultarEstadisticas;

/** GET /estadisticas — resumen para el dashboard de la web (JWT Bearer). */
@Controller("/estadisticas")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class EstadisticasController {

    private final ConsultarEstadisticas consultar;

    public EstadisticasController(ConsultarEstadisticas consultar) {
        this.consultar = consultar;
    }

    @Get
    @ExecuteOn(TaskExecutors.BLOCKING)
    public EstadisticasResponse obtener() {
        return EstadisticasResponse.from(consultar.obtener());
    }
}
