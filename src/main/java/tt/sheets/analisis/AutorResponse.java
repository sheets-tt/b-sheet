package tt.sheets.analisis;

import io.micronaut.serde.annotation.Serdeable;
import tt.sheets.analisis.domain.Autor;

/** {"id", "nombre"} (nombre_completo de la cuenta) o null si no se sabe quién fue. Nunca el correo. */
@Serdeable
public record AutorResponse(String id, String nombre) {

    public static AutorResponse from(Autor a) {
        return a == null ? null : new AutorResponse(a.id().toString(), a.nombre());
    }
}
