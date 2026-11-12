package tt.sheets.auth;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /auth/login. Los límites de largo frenan cuerpos abusivos (400 antes de tocar la BD):
 * 254 es el máximo de una dirección de correo (RFC 5321). La contraseña admite 256 caracteres y NO se
 * corta a los 72 bytes de BCrypt: TT_BKN no limita el largo al registrar, y su dueño debe poder entrar.
 */
@Serdeable
public record LoginRequest(@NotBlank @Size(max = 254) String username,
                           @NotBlank @Size(max = 256) String password) {
}
