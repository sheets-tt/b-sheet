package tt.sheets.estadistica.domain;

import tt.sheets.analisis.domain.Cromatico;
import tt.sheets.analisis.domain.EstadoSincronizacion;
import tt.sheets.analisis.domain.Resultado;
import tt.sheets.analisis.domain.TipoImagen;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Puerto de salida (solo lectura). Los conteos solo incluyen los valores que existen en la BD. */
public interface EstadisticasRepository {

    Totales totales();

    Map<Resultado, Long> contarPorResultado();

    Map<TipoImagen, Long> contarPorTipo();

    Map<EstadoSincronizacion, Long> contarPorSincronizacion();

    /** Promedio de cada porcentaje de analisis_cromatico, redondeado a 2 decimales. */
    Optional<Cromatico> cromaticoPromedio();

    Optional<Instant> ultimaCaptura();
}
