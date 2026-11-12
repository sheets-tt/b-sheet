package tt.sheets.estadistica.infrastructure;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tt.sheets.analisis.domain.Cromatico;
import tt.sheets.analisis.domain.EstadoSincronizacion;
import tt.sheets.analisis.domain.Resultado;
import tt.sheets.analisis.domain.TipoImagen;
import tt.sheets.estadistica.domain.EstadisticasRepository;
import tt.sheets.estadistica.domain.Totales;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Conteos y promedios contra db/demo: 3 árboles (uno sin análisis), 2 zonas, 5 análisis (e1..e5), 1 dispositivo. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcEstadisticasRepositoryTest {

    private ApplicationContext context;
    private EstadisticasRepository estadisticas;

    @BeforeAll
    void start() {
        context = ApplicationContext.run("test");
        estadisticas = context.getBean(EstadisticasRepository.class);
    }

    @AfterAll
    void stop() {
        context.close();
    }

    @Test
    void totalsCountRowsAndInfestedTreesAndZones() {
        // Infestados: e1 (árbol 1, corteza) y e5 (zona 2, follaje).
        assertEquals(new Totales(3, 2, 5, 1, 1, 1), estadisticas.totales());
    }

    @Test
    void countsGroupByDatabaseValue() {
        assertEquals(Map.of(Resultado.SANO, 2L, Resultado.INFESTADO, 2L, Resultado.NO_DETERMINADO, 1L),
                estadisticas.contarPorResultado());
        assertEquals(Map.of(TipoImagen.CORTEZA, 2L, TipoImagen.FOLLAJE, 3L), estadisticas.contarPorTipo());
        assertEquals(Map.of(EstadoSincronizacion.PENDIENTE, 1L, EstadoSincronizacion.SINCRONIZADO, 3L,
                EstadoSincronizacion.ERROR, 1L), estadisticas.contarPorSincronizacion());
    }

    @Test
    void chromaticAverageIsRoundedToTwoDecimals() {
        // e3 (80/10/5/5) y e4 (30/20/10/40).
        assertEquals(Optional.of(new Cromatico(55.0, 15.0, 7.5, 22.5)), estadisticas.cromaticoPromedio());
    }

    @Test
    void lastCaptureIsTheMostRecentAnalysis() {
        assertEquals(Optional.of(Instant.parse("2026-10-08T11:00:00Z")), estadisticas.ultimaCaptura());
    }
}
