package tt.sheets.administracion.domain;

/** 400: la contraseña nueva no cumple las reglas de {@code Contrasenas}. */
public class ContrasenaInvalidaException extends RuntimeException {

    public ContrasenaInvalidaException(String message) {
        super(message);
    }
}
