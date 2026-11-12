package tt.sheets.zona.infrastructure;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tt.sheets.analisis.domain.Resultado;
import tt.sheets.analisis.domain.UltimoAnalisis;
import tt.sheets.zona.domain.ZonaFollaje;
import tt.sheets.zona.domain.ZonaFollajeRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Adaptador contra la BD de tests (réplica del esquema de TT_BKN + datos de db/demo). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcZonaFollajeRepositoryTest {

    static final ZonaFollaje LAGO = new ZonaFollaje(UUID.fromString("c0000000-0000-0000-0000-000000000001"),
            "Zona lago menor", 19.421, -99.1825, 50.0, Instant.parse("2026-10-02T16:00:00Z"),
            "Dosel mixto de oyameles", new UltimoAnalisis(UUID.fromString("e0000000-0000-0000-0000-000000000004"), Resultado.NO_DETERMINADO, Instant.parse("2026-10-07T09:00:00Z")));
    static final ZonaFollaje CALZADA = new ZonaFollaje(UUID.fromString("c0000000-0000-0000-0000-000000000002"),
            "Zona calzada", 19.422, -99.184, null, Instant.parse("2026-10-06T18:00:00Z"), null,
            new UltimoAnalisis(UUID.fromString("e0000000-0000-0000-0000-000000000005"), Resultado.INFESTADO, Instant.parse("2026-10-08T11:00:00Z")));

    private ApplicationContext context;
    private ZonaFollajeRepository zonas;

    @BeforeAll
    void start() {
        context = ApplicationContext.run("test");
        zonas = context.getBean(ZonaFollajeRepository.class);
    }

    @AfterAll
    void stop() {
        context.close();
    }

    @Test
    void findAllReturnsMostRecentFirst() {
        assertEquals(List.of(CALZADA, LAGO), zonas.findAll());
    }

    @Test
    void findByIdReturnsZoneWithNullableColumns() {
        assertEquals(Optional.of(CALZADA), zonas.findById(CALZADA.id()));
    }

    @Test
    void findByIdIsEmptyForUnknownId() {
        assertEquals(Optional.empty(), zonas.findById(UUID.randomUUID()));
    }
}
