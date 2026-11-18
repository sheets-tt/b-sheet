package tt.sheets.arbol;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import tt.sheets.analisis.AutorResponse;
import tt.sheets.analisis.SeguidorResponse;
import tt.sheets.analisis.UltimoAnalisisResponse;
import tt.sheets.analisis.domain.Seguidor;
import tt.sheets.arbol.domain.Arbol;

import java.util.List;

/**
 * JSON de GET /arboles/{id}: lo mismo que {@link ArbolResponse} más {@code seguimiento}, las personas que han
 * subido análisis de este árbol (la más reciente primero; vacío si no hay autores conocidos).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Serdeable
public record ArbolDetalleResponse(String id, String nombreEspecifico, String especie, Double latitud,
                                   Double longitud, String fechaRegistro, String descripcion,
                                   UltimoAnalisisResponse ultimoAnalisis, AutorResponse registradoPor,
                                   List<SeguidorResponse> seguimiento) {

    static ArbolDetalleResponse from(Arbol a, List<Seguidor> seguimiento) {
        ArbolResponse r = ArbolResponse.from(a);
        return new ArbolDetalleResponse(r.id(), r.nombreEspecifico(), r.especie(), r.latitud(), r.longitud(),
                r.fechaRegistro(), r.descripcion(), r.ultimoAnalisis(), r.registradoPor(),
                SeguidorResponse.from(seguimiento));
    }
}
