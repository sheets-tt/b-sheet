package tt.sheets.analisis.domain;

/** Resultado de la clasificación (CHECK de la tabla analisis en TT_BKN). */
public enum Resultado implements ValorBd {
    SANO("sano"), INFESTADO("infestado"), NO_DETERMINADO("no_determinado");

    private final String valor;

    Resultado(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static Resultado desde(String valor) {
        return ValorBd.desde(Resultado.class, "resultado", valor);
    }
}
