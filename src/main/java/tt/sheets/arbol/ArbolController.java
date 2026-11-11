package tt.sheets.arbol;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import tt.sheets.arbol.application.ConsultarArboles;

import java.util.List;
import java.util.UUID;

/** GET /arboles y GET /arboles/{id} — consulta de árboles registrados en campo (JWT Bearer). */
@Controller("/arboles")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ArbolController {

    private final ConsultarArboles consultar;

    public ArbolController(ConsultarArboles consultar) {
        this.consultar = consultar;
    }

    @Get
    @ExecuteOn(TaskExecutors.BLOCKING)
    public List<ArbolResponse> listar() {
        return consultar.listar().stream().map(ArbolResponse::from).toList();
    }

    @Get("/{id}")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<ArbolResponse> obtener(@PathVariable UUID id) {
        return consultar.obtener(id)
                .map(a -> HttpResponse.ok(ArbolResponse.from(a)))
                .orElseGet(HttpResponse::notFound);
    }
}
