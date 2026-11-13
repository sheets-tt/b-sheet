package tt.sheets.administracion;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * Cuerpo de POST /cuentas. Solo estos cuatro campos: cualquier otro (estado, hash, id, fecha) se ignora, así
 * que una cuenta nueva siempre nace activa y con el hash que calcula el servidor. El correo y el nombre se
 * validan ya sin espacios alrededor; las reglas de la contraseña (8 caracteres a 72 bytes) están en
 * {@link tt.sheets.administracion.domain.Contrasenas}.
 */
@Serdeable
public record CrearCuentaRequest(
        @NotBlank(message = MensajesCuenta.CORREO_OBLIGATORIO)
        @Email(message = MensajesCuenta.CORREO_INVALIDO)
        @Size(max = 254, message = MensajesCuenta.CORREO_LARGO) String correo,
        @NotBlank(message = MensajesCuenta.NOMBRE_OBLIGATORIO)
        @Size(max = 255, message = MensajesCuenta.NOMBRE_LARGO) String nombreCompleto,
        @NotBlank(message = MensajesCuenta.ROL_INVALIDO)
        @Pattern(regexp = MensajesCuenta.ROLES, message = MensajesCuenta.ROL_INVALIDO) String rol,
        @Nullable String contrasena) {

    public CrearCuentaRequest {
        correo = correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
        nombreCompleto = nombreCompleto == null ? null : nombreCompleto.trim();
    }

    @Override
    public String toString() {
        return "CrearCuentaRequest[correo=" + correo + ", rol=" + rol + "]"; // sin la contraseña
    }
}
