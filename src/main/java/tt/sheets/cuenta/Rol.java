package tt.sheets.cuenta;

/** Rol de la cuenta. La BD de TT_BKN guarda {@link #valorBd}; la API y el JWT usan el nombre del enum. */
public enum Rol {
    ADMINISTRADOR("Administrador"),
    EXPERTO_CAMPO("Experto de campo");

    private final String valorBd;

    Rol(String valorBd) {
        this.valorBd = valorBd;
    }

    public static Rol desdeBd(String valor) {
        for (Rol rol : values()) {
            if (rol.valorBd.equals(valor)) {
                return rol;
            }
        }
        throw new IllegalArgumentException("Rol desconocido en la BD: " + valor);
    }
}
