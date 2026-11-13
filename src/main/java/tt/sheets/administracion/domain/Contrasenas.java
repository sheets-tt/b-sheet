package tt.sheets.administracion.domain;

import java.nio.charset.StandardCharsets;

/**
 * Reglas para una contraseña nueva. Máximo 72 bytes en UTF-8 porque BCrypt ignora lo que sigue: con más,
 * dos contraseñas distintas con el mismo inicio serían la misma. (El login sí acepta más, por las cuentas
 * que TT_BKN creó sin ese límite.)
 */
public final class Contrasenas {

    public static final int MINIMO_CARACTERES = 8;
    public static final int MAXIMO_BYTES = 72;

    private Contrasenas() {
    }

    public static void validar(String contrasena) {
        if (contrasena == null || contrasena.isBlank()) {
            throw new ContrasenaInvalidaException("La contraseña es obligatoria");
        }
        if (contrasena.codePointCount(0, contrasena.length()) < MINIMO_CARACTERES) {
            throw new ContrasenaInvalidaException("La contraseña debe tener al menos " + MINIMO_CARACTERES + " caracteres");
        }
        if (contrasena.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
            throw new ContrasenaInvalidaException("La contraseña no puede pasar de " + MAXIMO_BYTES + " bytes");
        }
    }
}
