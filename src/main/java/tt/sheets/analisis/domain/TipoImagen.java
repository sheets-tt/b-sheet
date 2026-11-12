package tt.sheets.analisis.domain;

/** Tipo de imagen analizada (CHECK de la tabla analisis en TT_BKN). */
public enum TipoImagen implements ValorBd {
    CORTEZA("corteza"), FOLLAJE("follaje");

    private final String valor;

    TipoImagen(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static TipoImagen desde(String valor) {
        return ValorBd.desde(TipoImagen.class, "tipo", valor);
    }
}
