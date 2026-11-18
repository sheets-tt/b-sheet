package tt.sheets.analisis;

import io.micronaut.serde.annotation.Serdeable;
import tt.sheets.analisis.domain.Seguidor;

import java.util.List;

/** Elemento de "seguimiento": {"id", "nombre", "analisis" (cuántos subió), "ultimo" (captura más reciente, ISO-8601 UTC)}. */
@Serdeable
public record SeguidorResponse(String id, String nombre, int analisis, String ultimo) {

    public static List<SeguidorResponse> from(List<Seguidor> seguidores) {
        return seguidores.stream()
                .map(s -> new SeguidorResponse(s.id().toString(), s.nombre(), s.analisis(),
                        s.ultimo() == null ? null : s.ultimo().toString()))
                .toList();
    }
}
