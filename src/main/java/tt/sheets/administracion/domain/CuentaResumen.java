package tt.sheets.administracion.domain;

import tt.sheets.cuenta.Rol;

import java.time.Instant;
import java.util.UUID;

/** Cuenta vista por un administrador. Nunca incluye el hash de la contraseña. */
public record CuentaResumen(UUID id, String correo, String nombreCompleto, Rol rol, boolean activa,
                            Instant fechaCreacion) {
}
