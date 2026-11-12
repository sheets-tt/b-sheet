package tt.sheets.analisis;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import tt.sheets.analisis.domain.Analisis;
import tt.sheets.analisis.domain.Cromatico;

import java.time.Instant;
import java.util.UUID;

/**
 * JSON de un análisis. Tipo, resultado y estado van con los valores de la BD ("follaje", "no_determinado",
 * "pendiente"...). Claves nulas incluidas; fechas en ISO-8601 UTC.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Serdeable
public record AnalisisResponse(String id, boolean tieneEvidencia, Double latitudCaptura, Double longitudCaptura,
                               String fechaCaptura, String fechaSincronizacion, String estadoSincronizacion,
                               String tipoImagen, String resultadoClasificacion, Double confianza,
                               String observaciones, String idArbol, String idZona, CromaticoResponse cromatico) {

    @Serdeable
    public record CromaticoResponse(Double pctSano, Double pctRegular, Double pctDanado, Double pctIncierto) {

        public static CromaticoResponse from(Cromatico c) {
            return c == null ? null : new CromaticoResponse(c.pctSano(), c.pctRegular(), c.pctDanado(), c.pctIncierto());
        }
    }

    /** {@code tieneEvidencia}: hay foto que se puede pedir a /analisis/{id}/evidencia (la ruta interna de S3 no se expone). */
    static AnalisisResponse from(Analisis a, boolean tieneEvidencia) {
        return new AnalisisResponse(a.id().toString(), tieneEvidencia, a.latitudCaptura(), a.longitudCaptura(),
                texto(a.fechaCaptura()), texto(a.fechaSincronizacion()), a.estadoSincronizacion().valor(),
                a.tipoImagen().valor(), a.resultadoClasificacion().valor(), a.confianza(), a.observaciones(),
                texto(a.idArbol()), texto(a.idZona()), CromaticoResponse.from(a.cromatico()));
    }

    private static String texto(Instant i) {
        return i == null ? null : i.toString();
    }

    private static String texto(UUID u) {
        return u == null ? null : u.toString();
    }
}
