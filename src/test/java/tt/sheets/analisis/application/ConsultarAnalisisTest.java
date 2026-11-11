package tt.sheets.analisis.application;

import org.junit.jupiter.api.Test;
import tt.sheets.analisis.domain.Analisis;
import tt.sheets.analisis.domain.AnalisisRepository;
import tt.sheets.analisis.domain.FiltroAnalisis;
import tt.sheets.analisis.domain.TipoImagen;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Reglas del caso de uso: rango de fechas y límite. El puerto es un doble que registra los filtros. */
class ConsultarAnalisisTest {

    private static final Instant OCT_1 = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant OCT_9 = Instant.parse("2026-10-09T00:00:00Z");

    private final List<FiltroAnalisis> recibidos = new ArrayList<>();
    private final AnalisisRepository analisis = new AnalisisRepository() {
        @Override
        public List<Analisis> buscar(FiltroAnalisis filtro) {
            recibidos.add(filtro);
            return List.of();
        }

        @Override
        public Optional<Analisis> findById(UUID id) {
            return Optional.empty();
        }
    };

    private final ConsultarAnalisis consultar = new ConsultarAnalisis(analisis);

    private static FiltroAnalisis filtro(Instant desde, Instant hasta, int limite) {
        return new FiltroAnalisis(TipoImagen.FOLLAJE, null, null, desde, hasta, null, null, limite);
    }

    @Test
    void buscarPassesValidFilterToRepository() {
        FiltroAnalisis valido = filtro(OCT_1, OCT_9, 100);
        consultar.buscar(valido);
        assertEquals(List.of(valido), recibidos);
    }

    @Test
    void buscarAcceptsSameInstantAsRange() {
        consultar.buscar(filtro(OCT_1, OCT_1, 1));
        assertEquals(1, recibidos.size());
    }

    @Test
    void buscarRejectsReversedDateRange() {
        FiltroInvalidoException e = assertThrows(FiltroInvalidoException.class,
                () -> consultar.buscar(filtro(OCT_9, OCT_1, 100)));
        assertEquals("desde no puede ser posterior a hasta", e.getMessage());
        assertEquals(List.of(), recibidos);
    }

    @Test
    void buscarRejectsLimitOutOfRange() {
        assertThrows(FiltroInvalidoException.class, () -> consultar.buscar(filtro(null, null, 0)));
        assertThrows(FiltroInvalidoException.class,
                () -> consultar.buscar(filtro(null, null, ConsultarAnalisis.LIMITE_MAXIMO + 1)));
        assertEquals(List.of(), recibidos);
    }
}
