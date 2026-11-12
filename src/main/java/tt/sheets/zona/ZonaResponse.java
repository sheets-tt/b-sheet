package tt.sheets.zona;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import tt.sheets.analisis.UltimoAnalisisResponse;
import tt.sheets.zona.domain.ZonaFollaje;

/** JSON de una zona de follaje. Las claves nulas se envían y la fecha va en ISO-8601 UTC. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Serdeable
public record ZonaResponse(String id, String nombreZona, Double latitudCentro, Double longitudCentro,
                           Double radioMetros, String fechaRegistro, String descripcion,
                           UltimoAnalisisResponse ultimoAnalisis) {

    static ZonaResponse from(ZonaFollaje z) {
        return new ZonaResponse(z.id().toString(), z.nombreZona(), z.latitudCentro(), z.longitudCentro(),
                z.radioMetros(), z.fechaRegistro() == null ? null : z.fechaRegistro().toString(), z.descripcion(),
                UltimoAnalisisResponse.from(z.ultimoAnalisis()));
    }
}
