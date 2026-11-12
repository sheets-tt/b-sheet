package tt.sheets.arbol.application;

import org.junit.jupiter.api.Test;
import tt.sheets.arbol.domain.Arbol;
import tt.sheets.arbol.domain.ArbolRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Caso de uso puro: el puerto se sustituye por un doble en memoria. */
class ConsultarArbolesTest {

    private static final Arbol OYAMEL = new Arbol(UUID.fromString("b0000000-0000-0000-0000-000000000001"),
            "Oyamel 1", "Abies religiosa", 19.42, -99.18, Instant.parse("2026-10-01T15:00:00Z"), null, null);

    private final ArbolRepository arboles = new ArbolRepository() {
        @Override
        public List<Arbol> findAll() {
            return List.of(OYAMEL);
        }

        @Override
        public Optional<Arbol> findById(UUID id) {
            return OYAMEL.id().equals(id) ? Optional.of(OYAMEL) : Optional.empty();
        }
    };

    private final ConsultarArboles consultar = new ConsultarArboles(arboles);

    @Test
    void listarReturnsEveryTree() {
        assertEquals(List.of(OYAMEL), consultar.listar());
    }

    @Test
    void obtenerFindsTreeById() {
        assertEquals(Optional.of(OYAMEL), consultar.obtener(OYAMEL.id()));
    }

    @Test
    void obtenerIsEmptyForUnknownId() {
        assertEquals(Optional.empty(), consultar.obtener(UUID.randomUUID()));
    }
}
