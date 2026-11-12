package tt.sheets.analisis.application;

import tt.sheets.analisis.domain.Analisis;
import tt.sheets.analisis.domain.AnalisisRepository;
import tt.sheets.analisis.domain.FiltroAnalisis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Caso de uso: búsqueda de análisis para la web (solo lectura). */
public class ConsultarAnalisis {

    /** Máximo de filas por consulta: la Lambda tiene poca memoria y la web pagina por fechas. */
    public static final int LIMITE_MAXIMO = 500;

    private final AnalisisRepository analisis;

    public ConsultarAnalisis(AnalisisRepository analisis) {
        this.analisis = analisis;
    }

    /** @throws FiltroInvalidoException si el rango de fechas está invertido o el límite fuera de 1..500. */
    public List<Analisis> buscar(FiltroAnalisis filtro) {
        if (filtro.limite() < 1 || filtro.limite() > LIMITE_MAXIMO) {
            throw new FiltroInvalidoException("limite debe estar entre 1 y " + LIMITE_MAXIMO);
        }
        if (filtro.desde() != null && filtro.hasta() != null && filtro.desde().isAfter(filtro.hasta())) {
            throw new FiltroInvalidoException("desde no puede ser posterior a hasta");
        }
        return analisis.buscar(filtro);
    }

    public Optional<Analisis> obtener(UUID id) {
        return analisis.findById(id);
    }
}
