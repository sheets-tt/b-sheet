package tt.sheets.analisis.domain;

/** Estado de sincronización desde el móvil (CHECK de la tabla analisis en TT_BKN). */
public enum EstadoSincronizacion implements ValorBd {
    PENDIENTE("pendiente"), SINCRONIZADO("sincronizado"), ERROR("error");

    private final String valor;

    EstadoSincronizacion(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static EstadoSincronizacion desde(String valor) {
        return ValorBd.desde(EstadoSincronizacion.class, "estado", valor);
    }
}
