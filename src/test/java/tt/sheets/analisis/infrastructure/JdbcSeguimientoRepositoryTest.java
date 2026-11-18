package tt.sheets.analisis.infrastructure;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tt.sheets.analisis.domain.Seguidor;
import tt.sheets.analisis.domain.SeguimientoRepository;
import tt.sheets.compartido.infrastructure.ColumnaAutorAnalisis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * db/demo: el Oyamel tiene e6 (campo, 10-05) y e1 (demo, 10-03); la Zona lago menor, e3 (demo, 10-04) y
 * e4 (campo, 10-07); la Zona calzada solo e5, sin autor.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSeguimientoRepositoryTest {

    private static final UUID DEMO = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID CAMPO = UUID.fromString("a0000000-0000-0000-0000-000000000004");

    private ApplicationContext context;
    private SeguimientoRepository seguimiento;

    @BeforeAll
    void start() {
        context = ApplicationContext.run("test");
        seguimiento = context.getBean(SeguimientoRepository.class);
    }

    @AfterAll
    void stop() {
        context.close();
    }

    @Test
    void treeFollowedByTwoPeopleMostRecentFirst() {
        assertEquals(List.of(
                new Seguidor(CAMPO, "Experta de Campo Demo", 1, Instant.parse("2026-10-05T15:00:00Z")),
                new Seguidor(DEMO, "Usuario Demo", 1, Instant.parse("2026-10-03T15:10:00Z"))),
                seguimiento.deArbol(UUID.fromString("b0000000-0000-0000-0000-000000000001")));
    }

    @Test
    void zoneOrderedByLatestCaptureNotById() {
        assertEquals(List.of(CAMPO, DEMO), seguimiento.deZona(UUID.fromString("c0000000-0000-0000-0000-000000000001"))
                .stream().map(Seguidor::id).toList());
    }

    @Test
    void analysesWithoutAuthorAreLeftOut() {
        assertEquals(List.of(), seguimiento.deZona(UUID.fromString("c0000000-0000-0000-0000-000000000002")));
        assertEquals(List.of(), seguimiento.deArbol(UUID.fromString("b0000000-0000-0000-0000-000000000003")));
        assertEquals(List.of(), seguimiento.deArbol(UUID.randomUUID()));
    }

    @Test
    void withoutTheColumnIsEmpty() {
        JdbcOperations jdbc = context.getBean(JdbcOperations.class);
        SeguimientoRepository sinColumna = new JdbcSeguimientoRepository(jdbc, new ColumnaAutorAnalisis(jdbc) {
            @Override
            public boolean disponible() {
                return false;
            }
        });
        assertEquals(List.of(), sinColumna.deArbol(UUID.fromString("b0000000-0000-0000-0000-000000000001")));
        assertEquals(List.of(), sinColumna.deZona(UUID.fromString("c0000000-0000-0000-0000-000000000001")));
    }
}
