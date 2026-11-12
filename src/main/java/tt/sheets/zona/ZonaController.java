package tt.sheets.zona;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import tt.sheets.zona.application.ConsultarZonas;

import java.util.List;
import java.util.UUID;

/** GET /zonas y GET /zonas/{id} — consulta de zonas de follaje (JWT Bearer). */
@Controller("/zonas")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ZonaController {

    private final ConsultarZonas consultar;

    public ZonaController(ConsultarZonas consultar) {
        this.consultar = consultar;
    }

    @Get
    @ExecuteOn(TaskExecutors.BLOCKING)
    public List<ZonaResponse> listar() {
        return consultar.listar().stream().map(ZonaResponse::from).toList();
    }

    @Get("/{id}")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<ZonaResponse> obtener(@PathVariable UUID id) {
        return consultar.obtener(id)
                .map(z -> HttpResponse.ok(ZonaResponse.from(z)))
                .orElseGet(HttpResponse::notFound);
    }
}
