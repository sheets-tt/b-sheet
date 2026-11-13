package tt.sheets.administracion;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * Cuerpo de PUT /cuentas/{id}: mismas reglas que {@link CrearCuentaRequest}, pero {@code contrasena} ausente,
 * null o en blanco conserva la actual. El estado no se cambia aquí (PATCH .../desactivar y .../activar).
 */
@Serdeable
public record EditarCuentaRequest(
        @NotBlank(message = MensajesCuenta.CORREO_OBLIGATORIO)
        @Email(message = MensajesCuenta.CORREO_INVALIDO)
        @Size(max = 254, message = MensajesCuenta.CORREO_LARGO) String correo,
        @NotBlank(message = MensajesCuenta.NOMBRE_OBLIGATORIO)
        @Size(max = 255, message = MensajesCuenta.NOMBRE_LARGO) String nombreCompleto,
        @NotBlank(message = MensajesCuenta.ROL_INVALIDO)
        @Pattern(regexp = MensajesCuenta.ROLES, message = MensajesCuenta.ROL_INVALIDO) String rol,
        @Nullable String contrasena) {

    public EditarCuentaRequest {
        correo = correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
        nombreCompleto = nombreCompleto == null ? null : nombreCompleto.trim();
    }

    @Override
    public String toString() {
        return "EditarCuentaRequest[correo=" + correo + ", rol=" + rol + "]"; // sin la contraseña
    }
}
