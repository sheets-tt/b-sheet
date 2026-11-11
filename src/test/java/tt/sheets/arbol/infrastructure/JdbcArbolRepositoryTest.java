package tt.sheets.arbol.infrastructure;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tt.sheets.analisis.domain.Resultado;
import tt.sheets.analisis.domain.UltimoAnalisis;
import tt.sheets.arbol.domain.Arbol;
import tt.sheets.arbol.domain.ArbolRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Adaptador contra la BD de tests (réplica del esquema de TT_BKN + datos de db/demo). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcArbolRepositoryTest {

    static final Arbol OYAMEL = new Arbol(UUID.fromString("b0000000-0000-0000-0000-000000000001"),
            "Oyamel del lago", "Abies religiosa", 19.420500, -99.181800,
            Instant.parse("2026-10-01T15:00:00Z"), "Junto al lago menor",
            new UltimoAnalisis(UUID.fromString("e0000000-0000-0000-0000-000000000001"), Resultado.INFESTADO, Instant.parse("2026-10-03T15:10:00Z")));
    static final Arbol AHUEHUETE = new Arbol(UUID.fromString("b0000000-0000-0000-0000-000000000002"),
            "Ahuehuete de la calzada", "Taxodium mucronatum", 19.421300, -99.183100,
            Instant.parse("2026-10-05T17:30:00Z"), null,
            new UltimoAnalisis(UUID.fromString("e0000000-0000-0000-0000-000000000002"), Resultado.SANO, Instant.parse("2026-10-06T10:00:00Z")));
    static final Arbol PINO = new Arbol(UUID.fromString("b0000000-0000-0000-0000-000000000003"),
            "Pino del cerro", "Pinus hartwegii", 19.4198, -99.1845, Instant.parse("2026-10-07T12:00:00Z"), null, null);

    private ApplicationContext context;
    private ArbolRepository arboles;

    @BeforeAll
    void start() {
        context = ApplicationContext.run("test");
        arboles = context.getBean(ArbolRepository.class);
    }

    @AfterAll
    void stop() {
        context.close();
    }

    @Test
    void findAllReturnsMostRecentFirst() {
        assertEquals(List.of(PINO, AHUEHUETE, OYAMEL), arboles.findAll());
    }

    @Test
    void findByIdReturnsTree() {
        assertEquals(Optional.of(OYAMEL), arboles.findById(OYAMEL.id()));
    }

    @Test
    void lastAnalysisIsNullWithoutAnalyses() {
        assertEquals(Optional.of(PINO), arboles.findById(PINO.id()));
    }

    @Test
    void findByIdIsEmptyForUnknownId() {
        assertEquals(Optional.empty(), arboles.findById(UUID.randomUUID()));
    }
}
