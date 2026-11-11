package tt.sheets.analisis;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import tt.sheets.analisis.application.ConsultarAnalisis;
import tt.sheets.analisis.application.ObtenerEvidencia;
import tt.sheets.analisis.domain.Analisis;
import tt.sheets.analisis.domain.EstadoSincronizacion;
import tt.sheets.analisis.domain.FiltroAnalisis;
import tt.sheets.analisis.domain.Resultado;
import tt.sheets.analisis.domain.TipoImagen;
import tt.sheets.auth.ErrorResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * GET /analisis — búsqueda con filtros opcionales: tipo, resultado, estado, desde, hasta (ISO-8601),
 * arbol, zona (UUID) y limite (1..500, 100 por defecto). GET /analisis/{id} — detalle. GET /analisis/{id}/evidencia — foto. JWT Bearer.
 */
@Controller("/analisis")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class AnalisisController {

    private final ConsultarAnalisis consultar;
    private final ObtenerEvidencia evidencias;

    public AnalisisController(ConsultarAnalisis consultar, ObtenerEvidencia evidencias) {
        this.consultar = consultar;
        this.evidencias = evidencias;
    }

    private AnalisisResponse respuesta(Analisis a) {
        return AnalisisResponse.from(a, evidencias.disponible(a));
    }

    @Get
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<?> buscar(@Nullable @QueryValue String tipo,
                                  @Nullable @QueryValue String resultado,
                                  @Nullable @QueryValue String estado,
                                  @Nullable @QueryValue Instant desde,
                                  @Nullable @QueryValue Instant hasta,
                                  @Nullable @QueryValue UUID arbol,
                                  @Nullable @QueryValue UUID zona,
                                  @QueryValue(defaultValue = "100") int limite) {
        try {
            FiltroAnalisis filtro = new FiltroAnalisis(
                    tipo == null ? null : TipoImagen.desde(tipo),
                    resultado == null ? null : Resultado.desde(resultado),
                    estado == null ? null : EstadoSincronizacion.desde(estado),
                    desde, hasta, arbol, zona, limite);
            return HttpResponse.ok(consultar.buscar(filtro).stream().map(this::respuesta).toList());
        } catch (IllegalArgumentException e) {
            // Valor de enum desconocido o FiltroInvalidoException (rango de fechas / límite).
            return HttpResponse.badRequest(new ErrorResponse(e.getMessage()));
        }
    }

    @Get("/{id}")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<AnalisisResponse> obtener(@PathVariable UUID id) {
        return consultar.obtener(id)
                .map(a -> HttpResponse.ok(respuesta(a)))
                .orElseGet(HttpResponse::notFound);
    }

    /** URL temporal (5 min) de la foto en S3. No se cachea: la firma caduca. */
    @Get("/{id}/evidencia")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<?> evidencia(@PathVariable UUID id) {
        return evidencias.obtener(id)
                .<HttpResponse<?>>map(e -> HttpResponse.ok(new EvidenciaResponse(e.url().toString(), e.expira().toString()))
                        .header(HttpHeaders.CACHE_CONTROL, "no-store"))
                .orElseGet(() -> HttpResponse.notFound(new ErrorResponse("Este análisis no tiene foto disponible.")));
    }
}
