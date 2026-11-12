package tt.sheets.administracion;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import tt.sheets.administracion.domain.DispositivoResumen;

/** JSON de un dispositivo para administración (sin token_dispositivo). */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Serdeable
public record DispositivoResumenResponse(String id, String identificadorUnico, String nombreDispositivo,
                                         String estado, String fechaRegistro, String cuentaVigente) {

    static DispositivoResumenResponse from(DispositivoResumen d) {
        return new DispositivoResumenResponse(d.id().toString(), d.identificadorUnico(), d.nombreDispositivo(),
                d.estado(), d.fechaRegistro() == null ? null : d.fechaRegistro().toString(), d.cuentaVigente());
    }
}
