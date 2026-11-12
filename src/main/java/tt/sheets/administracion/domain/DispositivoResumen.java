package tt.sheets.administracion.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Dispositivo visto por un administrador. {@code estado} va como en TT_BKN (Activo/inactivo/bloqueado);
 * {@code cuentaVigente} es el correo de la asociación sin desasociar, o null. Nunca incluye token_dispositivo.
 */
public record DispositivoResumen(UUID id, String identificadorUnico, String nombreDispositivo, String estado,
                                 Instant fechaRegistro, String cuentaVigente) {
}
