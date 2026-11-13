package tt.sheets.administracion.domain;

/** 404: no existe una cuenta con ese id. */
public class CuentaNoEncontradaException extends RuntimeException {

    public CuentaNoEncontradaException() {
        super("La cuenta no existe");
    }
}
