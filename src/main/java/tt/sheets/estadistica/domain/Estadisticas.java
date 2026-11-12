package tt.sheets.estadistica.domain;

import tt.sheets.analisis.domain.Cromatico;
import tt.sheets.analisis.domain.EstadoSincronizacion;
import tt.sheets.analisis.domain.Resultado;
import tt.sheets.analisis.domain.TipoImagen;

import java.time.Instant;
import java.util.Map;

/**
 * Resumen para el dashboard. Los mapas traen todas las categorías (0 si no hay datos) en el orden del enum.
 * {@code cromaticoPromedio} y {@code ultimaCaptura} son null si aún no hay datos.
 */
public record Estadisticas(Totales totales, Map<Resultado, Long> porResultado, Map<TipoImagen, Long> porTipo,
                           Map<EstadoSincronizacion, Long> porSincronizacion, Cromatico cromaticoPromedio,
                           Instant ultimaCaptura) {
}
