package tt.sheets.administracion.domain;

/** 409: otra cuenta ya usa ese correo (sin importar mayúsculas). */
public class CorreoEnUsoException extends RuntimeException {

    public CorreoEnUsoException() {
        super("Ya existe una cuenta con ese correo");
    }
}
