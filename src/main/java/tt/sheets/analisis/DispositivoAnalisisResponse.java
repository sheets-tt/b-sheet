package tt.sheets.analisis;

import io.micronaut.serde.annotation.Serdeable;
import tt.sheets.analisis.domain.DispositivoAnalisis;

/** {"id", "nombre", "codigo"} del dispositivo que tomó la foto, o null si no se registró. */
@Serdeable
public record DispositivoAnalisisResponse(String id, String nombre, String codigo) {

    public static DispositivoAnalisisResponse from(DispositivoAnalisis d) {
        return d == null ? null : new DispositivoAnalisisResponse(d.id().toString(), d.nombre(), d.codigo());
    }
}
