package tt.sheets.analisis.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida (solo lectura): la infraestructura (SQL) lo implementa. */
public interface AnalisisRepository {

    /** Análisis que cumplen el filtro, de la captura más reciente a la más antigua, hasta {@code limite}. */
    List<Analisis> buscar(FiltroAnalisis filtro);

    Optional<Analisis> findById(UUID id);
}
