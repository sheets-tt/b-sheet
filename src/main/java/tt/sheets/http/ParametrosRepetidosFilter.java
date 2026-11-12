package tt.sheets.http;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import tt.sheets.auth.ErrorResponse;

/**
 * Rechaza (400) un parámetro de query repetido, p. ej. ?tipo=corteza&tipo=follaje. Sin esto el
 * binding se queda con uno de los valores sin avisar y la respuesta no corresponde a lo pedido.
 * API Gateway (HTTP API v2) une los repetidos con comas; ningún parámetro de esta API lleva comas
 * (enums, UUID, fechas ISO-8601, enteros), así que una coma también cuenta como repetición.
 */
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
public class ParametrosRepetidosFilter {

    @RequestFilter
    @Nullable
    public HttpResponse<?> filtrar(HttpRequest<?> request) {
        for (String nombre : request.getParameters().names()) {
            var valores = request.getParameters().getAll(nombre);
            if (valores.size() > 1 || valores.stream().anyMatch(v -> v.contains(","))) {
                return HttpResponse.badRequest(new ErrorResponse("Parámetro repetido: " + nombre));
            }
        }
        return null;
    }
}
