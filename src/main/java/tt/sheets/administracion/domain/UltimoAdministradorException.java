package tt.sheets.administracion.domain;

/** 409: el cambio dejaría el sistema sin ningún administrador activo (p. ej., dos administradores que se desactivan a la vez). */
public class UltimoAdministradorException extends RuntimeException {

    public UltimoAdministradorException() {
        super("Debe quedar al menos un administrador activo");
    }
}
