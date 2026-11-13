package tt.sheets.administracion;

/** Mensajes de validación (400) de los cuerpos de alta y edición de cuentas. */
final class MensajesCuenta {

    static final String ROLES = "ADMINISTRADOR|EXPERTO_CAMPO";
    static final String CORREO_OBLIGATORIO = "El correo es obligatorio";
    static final String CORREO_INVALIDO = "El correo no es válido";
    static final String CORREO_LARGO = "El correo no puede pasar de 254 caracteres";
    static final String NOMBRE_OBLIGATORIO = "El nombre completo es obligatorio";
    static final String NOMBRE_LARGO = "El nombre completo no puede pasar de 255 caracteres";
    static final String ROL_INVALIDO = "El rol debe ser ADMINISTRADOR o EXPERTO_CAMPO";

    private MensajesCuenta() {
    }
}
