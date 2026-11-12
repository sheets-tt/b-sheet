package tt.sheets.arbol;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import tt.sheets.analisis.UltimoAnalisisResponse;
import tt.sheets.arbol.domain.Arbol;

/** JSON de un árbol. Las claves nulas se envían (no se omiten) y la fecha va en ISO-8601 UTC. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Serdeable
public record ArbolResponse(String id, String nombreEspecifico, String especie, Double latitud, Double longitud,
                            String fechaRegistro, String descripcion,
                            UltimoAnalisisResponse ultimoAnalisis) {

    static ArbolResponse from(Arbol a) {
        return new ArbolResponse(a.id().toString(), a.nombreEspecifico(), a.especie(), a.latitud(), a.longitud(),
                a.fechaRegistro() == null ? null : a.fechaRegistro().toString(), a.descripcion(),
                UltimoAnalisisResponse.from(a.ultimoAnalisis()));
    }
}
