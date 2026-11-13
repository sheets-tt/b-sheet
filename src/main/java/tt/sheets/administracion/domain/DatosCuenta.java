package tt.sheets.administracion.domain;

import tt.sheets.cuenta.Rol;

import java.util.Locale;

/**
 * Lo que un administrador fija de una cuenta. El correo se guarda sin espacios y en minúsculas (el UNIQUE
 * de TT_BKN distingue mayúsculas; así no se cuelan duplicados por capitalización) y el nombre sin espacios
 * alrededor. La contraseña va aparte: nunca forma parte de un modelo que se pueda devolver.
 */
public record DatosCuenta(String correo, String nombreCompleto, Rol rol) {

    public DatosCuenta {
        correo = correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
        nombreCompleto = nombreCompleto == null ? null : nombreCompleto.trim();
    }
}
