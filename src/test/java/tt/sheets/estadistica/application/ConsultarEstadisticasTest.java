package tt.sheets.estadistica.application;

import org.junit.jupiter.api.Test;
import tt.sheets.analisis.domain.Cromatico;
import tt.sheets.analisis.domain.EstadoSincronizacion;
import tt.sheets.analisis.domain.Resultado;
import tt.sheets.analisis.domain.TipoImagen;
import tt.sheets.estadistica.domain.Estadisticas;
import tt.sheets.estadistica.domain.EstadisticasRepository;
import tt.sheets.estadistica.domain.Totales;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** La BD solo devuelve los valores que existen; el caso de uso completa cada categoría con 0. */
class ConsultarEstadisticasTest {

    private static final Totales TOTALES = new Totales(2, 1, 3, 1, 1, 0);

    private static EstadisticasRepository repositorio(Map<Resultado, Long> resultados, Map<TipoImagen, Long> tipos,
                                                      Map<EstadoSincronizacion, Long> estados, Cromatico cromatico,
                                                      Instant ultima) {
        return new EstadisticasRepository() {
            public Totales totales() { return TOTALES; }
            public Map<Resultado, Long> contarPorResultado() { return resultados; }
            public Map<TipoImagen, Long> contarPorTipo() { return tipos; }
            public Map<EstadoSincronizacion, Long> contarPorSincronizacion() { return estados; }
            public Optional<Cromatico> cromaticoPromedio() { return Optional.ofNullable(cromatico); }
            public Optional<Instant> ultimaCaptura() { return Optional.ofNullable(ultima); }
        };
    }

    @Test
    void fillsMissingCategoriesWithZeroInDeclarationOrder() {
        Estadisticas e = new ConsultarEstadisticas(repositorio(Map.of(Resultado.INFESTADO, 3L),
                Map.of(TipoImagen.FOLLAJE, 3L), Map.of(EstadoSincronizacion.ERROR, 1L),
                new Cromatico(50.0, 20.0, 20.0, 10.0), Instant.parse("2026-10-08T11:00:00Z"))).obtener();

        assertEquals(TOTALES, e.totales());
        assertEquals(List.of(Resultado.SANO, Resultado.INFESTADO, Resultado.NO_DETERMINADO),
                List.copyOf(e.porResultado().keySet()));
        assertEquals(List.of(0L, 3L, 0L), List.copyOf(e.porResultado().values()));
        assertEquals(Map.of(TipoImagen.CORTEZA, 0L, TipoImagen.FOLLAJE, 3L), e.porTipo());
        assertEquals(Map.of(EstadoSincronizacion.PENDIENTE, 0L, EstadoSincronizacion.SINCRONIZADO, 0L,
                EstadoSincronizacion.ERROR, 1L), e.porSincronizacion());
        assertEquals(new Cromatico(50.0, 20.0, 20.0, 10.0), e.cromaticoPromedio());
        assertEquals(Instant.parse("2026-10-08T11:00:00Z"), e.ultimaCaptura());
    }

    @Test
    void emptyDatabaseHasZerosAndNoAverages() {
        Estadisticas e = new ConsultarEstadisticas(repositorio(Map.of(), Map.of(), Map.of(), null, null)).obtener();

        assertEquals(3, e.porResultado().size());
        assertEquals(Map.of(TipoImagen.CORTEZA, 0L, TipoImagen.FOLLAJE, 0L), e.porTipo());
        assertNull(e.cromaticoPromedio());
        assertNull(e.ultimaCaptura());
    }
}
