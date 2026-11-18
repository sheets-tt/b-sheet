package tt.sheets.zona;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import tt.sheets.analisis.AutorResponse;
import tt.sheets.analisis.SeguidorResponse;
import tt.sheets.analisis.UltimoAnalisisResponse;
import tt.sheets.analisis.domain.Seguidor;
import tt.sheets.zona.domain.ZonaFollaje;

import java.util.List;

/**
 * JSON de GET /zonas/{id}: lo mismo que {@link ZonaResponse} más {@code seguimiento}, las personas que han
 * subido análisis de esta zona (la más reciente primero; vacío si no hay autores conocidos).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Serdeable
public record ZonaDetalleResponse(String id, String nombreZona, Double latitudCentro, Double longitudCentro,
                                  Double radioMetros, String fechaRegistro, String descripcion,
                                  UltimoAnalisisResponse ultimoAnalisis, AutorResponse registradoPor,
                                  List<SeguidorResponse> seguimiento) {

    static ZonaDetalleResponse from(ZonaFollaje z, List<Seguidor> seguimiento) {
        ZonaResponse r = ZonaResponse.from(z);
        return new ZonaDetalleResponse(r.id(), r.nombreZona(), r.latitudCentro(), r.longitudCentro(),
                r.radioMetros(), r.fechaRegistro(), r.descripcion(), r.ultimoAnalisis(), r.registradoPor(),
                SeguidorResponse.from(seguimiento));
    }
}
