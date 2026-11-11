package tt.sheets.zona.application;

import org.junit.jupiter.api.Test;
import tt.sheets.zona.domain.ZonaFollaje;
import tt.sheets.zona.domain.ZonaFollajeRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Caso de uso puro: el puerto se sustituye por un doble en memoria. */
class ConsultarZonasTest {

    private static final ZonaFollaje LAGO = new ZonaFollaje(UUID.fromString("c0000000-0000-0000-0000-000000000001"),
            "Zona lago", 19.42, -99.18, 50.0, Instant.parse("2026-10-02T16:00:00Z"), null, null);

    private final ZonaFollajeRepository zonas = new ZonaFollajeRepository() {
        @Override
        public List<ZonaFollaje> findAll() {
            return List.of(LAGO);
        }

        @Override
        public Optional<ZonaFollaje> findById(UUID id) {
            return LAGO.id().equals(id) ? Optional.of(LAGO) : Optional.empty();
        }
    };

    private final ConsultarZonas consultar = new ConsultarZonas(zonas);

    @Test
    void listarReturnsEveryZone() {
        assertEquals(List.of(LAGO), consultar.listar());
    }

    @Test
    void obtenerFindsZoneById() {
        assertEquals(Optional.of(LAGO), consultar.obtener(LAGO.id()));
    }

    @Test
    void obtenerIsEmptyForUnknownId() {
        assertEquals(Optional.empty(), consultar.obtener(UUID.randomUUID()));
    }
}
