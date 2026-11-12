package tt.sheets.administracion;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import tt.sheets.administracion.domain.CuentaResumen;

/** JSON de una cuenta para administración: rol como código (ADMINISTRADOR / EXPERTO_CAMPO). */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Serdeable
public record CuentaResumenResponse(String id, String correo, String nombreCompleto, String rol, boolean activa,
                                    String fechaCreacion) {

    static CuentaResumenResponse from(CuentaResumen c) {
        return new CuentaResumenResponse(c.id().toString(), c.correo(), c.nombreCompleto(), c.rol().name(),
                c.activa(), c.fechaCreacion() == null ? null : c.fechaCreacion().toString());
    }
}
