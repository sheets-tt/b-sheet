package tt.sheets.administracion.domain;

/** 409: el administrador intentó desactivarse o quitarse el rol; se quedaría sin acceso a la administración. */
public class CuentaPropiaException extends RuntimeException {

    public CuentaPropiaException(String message) {
        super(message);
    }
}
