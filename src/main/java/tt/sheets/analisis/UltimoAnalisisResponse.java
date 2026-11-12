package tt.sheets.analisis;

import io.micronaut.serde.annotation.Serdeable;
import tt.sheets.analisis.domain.UltimoAnalisis;

/** {"id", "resultado" (valor de la BD), "fechaCaptura"} o null si aún no hay análisis. */
@Serdeable
public record UltimoAnalisisResponse(String id, String resultado, String fechaCaptura) {

    public static UltimoAnalisisResponse from(UltimoAnalisis u) {
        return u == null ? null : new UltimoAnalisisResponse(u.id().toString(), u.resultado().valor(),
                u.fechaCaptura() == null ? null : u.fechaCaptura().toString());
    }
}
