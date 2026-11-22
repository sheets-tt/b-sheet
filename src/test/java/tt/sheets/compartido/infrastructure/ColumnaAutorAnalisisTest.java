package tt.sheets.compartido.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La columna del autor se detecta sin redesplegar: mientras falte, se vuelve a revisar cada 5 minutos. */
class ColumnaAutorAnalisisTest {

    /** Reloj que se adelanta a mano. */
    private static final class RelojMovible extends Clock {
        private Instant ahora = Instant.parse("2026-11-21T12:00:00Z");

        void adelantar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private static ColumnaAutorAnalisis columna(RelojMovible reloj, AtomicBoolean existe, AtomicInteger consultas) {
        return new ColumnaAutorAnalisis(null, reloj) {
            @Override
            protected boolean consultar() {
                consultas.incrementAndGet();
                return existe.get();
            }
        };
    }

    @Test
    void siLaColumnaApareceDespuesSeDetectaSinRedesplegar() {
        RelojMovible reloj = new RelojMovible();
        AtomicBoolean existe = new AtomicBoolean(false);
        AtomicInteger consultas = new AtomicInteger();
        ColumnaAutorAnalisis columna = columna(reloj, existe, consultas);

        assertFalse(columna.disponible());
        assertEquals(1, consultas.get());

        // Se aplica la migración en TT_BKN: antes de 5 min no se vuelve a preguntar a la BD.
        existe.set(true);
        reloj.adelantar(Duration.ofMinutes(4));
        assertFalse(columna.disponible());
        assertEquals(1, consultas.get());

        reloj.adelantar(Duration.ofMinutes(1));
        assertTrue(columna.disponible());
        assertEquals(2, consultas.get());
    }

    @Test
    void siLaColumnaExisteSeRecuerdaYNoSeVuelveAConsultar() {
        RelojMovible reloj = new RelojMovible();
        AtomicInteger consultas = new AtomicInteger();
        ColumnaAutorAnalisis columna = columna(reloj, new AtomicBoolean(true), consultas);

        assertTrue(columna.disponible());
        reloj.adelantar(Duration.ofHours(2));
        assertTrue(columna.disponible());
        assertTrue(columna.disponible());
        assertEquals(1, consultas.get());
    }

    @Test
    void mientrasFalteSoloConsultaUnaVezPorIntervalo() {
        RelojMovible reloj = new RelojMovible();
        AtomicInteger consultas = new AtomicInteger();
        ColumnaAutorAnalisis columna = columna(reloj, new AtomicBoolean(false), consultas);

        for (int i = 0; i < 100; i++) {
            assertFalse(columna.disponible());
        }
        assertEquals(1, consultas.get());
        reloj.adelantar(ColumnaAutorAnalisis.REINTENTO);
        assertFalse(columna.disponible());
        assertEquals(2, consultas.get());
    }
}
