package tt.sheets.analisis.infrastructure;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tt.sheets.analisis.domain.Analisis;
import tt.sheets.analisis.domain.AnalisisRepository;
import tt.sheets.analisis.domain.Cromatico;
import tt.sheets.analisis.domain.EstadoSincronizacion;
import tt.sheets.analisis.domain.FiltroAnalisis;
import tt.sheets.analisis.domain.Resultado;
import tt.sheets.analisis.domain.TipoImagen;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Adaptador SQL contra la BD de tests (db/demo: e1..e5, cromático solo en e3 y e4). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAnalisisRepositoryTest {

    private static final UUID ARBOL_1 = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID ZONA_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    private ApplicationContext context;
    private AnalisisRepository analisis;

    @BeforeAll
    void start() {
        context = ApplicationContext.run("test");
        analisis = context.getBean(AnalisisRepository.class);
    }

    @AfterAll
    void stop() {
        context.close();
    }

    private static UUID e(int n) {
        return UUID.fromString("e0000000-0000-0000-0000-00000000000" + n);
    }

    private static FiltroAnalisis todos() {
        return new FiltroAnalisis(null, null, null, null, null, null, null, 100);
    }

    private List<UUID> ids(FiltroAnalisis filtro) {
        return analisis.buscar(filtro).stream().map(Analisis::id).toList();
    }

    @Test
    void withoutFiltersReturnsMostRecentCaptureFirst() {
        assertEquals(List.of(e(5), e(4), e(2), e(3), e(1)), ids(todos()));
    }

    @Test
    void filtersByTypeResultAndSyncState() {
        assertEquals(List.of(e(5), e(4), e(3)),
                ids(new FiltroAnalisis(TipoImagen.FOLLAJE, null, null, null, null, null, null, 100)));
        assertEquals(List.of(e(5), e(1)),
                ids(new FiltroAnalisis(null, Resultado.INFESTADO, null, null, null, null, null, 100)));
        assertEquals(List.of(e(4)),
                ids(new FiltroAnalisis(null, null, EstadoSincronizacion.PENDIENTE, null, null, null, null, 100)));
        assertEquals(List.of(e(5)),
                ids(new FiltroAnalisis(TipoImagen.FOLLAJE, Resultado.INFESTADO, null, null, null, null, null, 100)));
    }

    @Test
    void filtersByTreeAndZone() {
        assertEquals(List.of(e(1)), ids(new FiltroAnalisis(null, null, null, null, null, ARBOL_1, null, 100)));
        assertEquals(List.of(e(4), e(3)), ids(new FiltroAnalisis(null, null, null, null, null, null, ZONA_1, 100)));
    }

    @Test
    void dateRangeIsInclusive() {
        assertEquals(List.of(e(4), e(2), e(3)), ids(new FiltroAnalisis(null, null, null,
                Instant.parse("2026-10-04T12:00:00Z"), Instant.parse("2026-10-07T09:00:00Z"), null, null, 100)));
    }

    @Test
    void limitCutsTheMostRecent() {
        assertEquals(List.of(e(5), e(4)), ids(new FiltroAnalisis(null, null, null, null, null, null, null, 2)));
    }

    @Test
    void findByIdReturnsEveryColumn() {
        assertEquals(Optional.of(new Analisis(e(1), "evidencias/e1.jpg", 19.4205, -99.1818,
                Instant.parse("2026-10-03T15:10:00Z"), Instant.parse("2026-10-03T15:20:00Z"),
                EstadoSincronizacion.SINCRONIZADO, TipoImagen.CORTEZA, Resultado.INFESTADO, 0.91,
                "Galerías visibles", ARBOL_1, null, null)), analisis.findById(e(1)));
    }

    @Test
    void findByIdIncludesChromaticWhenPresent() {
        assertEquals(new Cromatico(80.0, 10.0, 5.0, 5.0), analisis.findById(e(3)).orElseThrow().cromatico());
        assertEquals(null, analisis.findById(e(5)).orElseThrow().cromatico());
    }

    @Test
    void findByIdIsEmptyForUnknownId() {
        assertEquals(Optional.empty(), analisis.findById(UUID.randomUUID()));
    }
}
