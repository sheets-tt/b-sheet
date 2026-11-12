package tt.sheets.analisis.application;

/** Filtro de búsqueda que no cumple las reglas del caso de uso (la API responde 400). */
public class FiltroInvalidoException extends IllegalArgumentException {

    public FiltroInvalidoException(String message) {
        super(message);
    }
}
