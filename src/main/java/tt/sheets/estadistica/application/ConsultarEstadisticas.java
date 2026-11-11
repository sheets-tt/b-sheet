package tt.sheets.estadistica.application;

import tt.sheets.analisis.domain.Resultado;
import tt.sheets.analisis.domain.EstadoSincronizacion;
import tt.sheets.analisis.domain.TipoImagen;
import tt.sheets.estadistica.domain.Estadisticas;
import tt.sheets.estadistica.domain.EstadisticasRepository;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Caso de uso: resumen del dashboard con todas las categorías presentes. */
public class ConsultarEstadisticas {

    private final EstadisticasRepository estadisticas;

    public ConsultarEstadisticas(EstadisticasRepository estadisticas) {
        this.estadisticas = estadisticas;
    }

    public Estadisticas obtener() {
        return new Estadisticas(
                estadisticas.totales(),
                completar(Resultado.class, estadisticas.contarPorResultado()),
                completar(TipoImagen.class, estadisticas.contarPorTipo()),
                completar(EstadoSincronizacion.class, estadisticas.contarPorSincronizacion()),
                estadisticas.cromaticoPromedio().orElse(null),
                estadisticas.ultimaCaptura().orElse(null));
    }

    /** Todas las constantes del enum, en orden de declaración, con 0 si la BD no trajo ese valor. */
    private static <E extends Enum<E>> Map<E, Long> completar(Class<E> tipo, Map<E, Long> conteos) {
        EnumMap<E, Long> completo = new EnumMap<>(tipo);
        for (E valor : tipo.getEnumConstants()) {
            completo.put(valor, conteos.getOrDefault(valor, 0L));
        }
        return Collections.unmodifiableMap(completo);
    }
}
