package tt.sheets.analisis.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Valores de texto de los CHECK de TT_BKN <-> enums del dominio. */
class ValoresAnalisisTest {

    @Test
    void tipoImagenMapsDatabaseValues() {
        assertEquals(TipoImagen.CORTEZA, TipoImagen.desde("corteza"));
        assertEquals(TipoImagen.FOLLAJE, TipoImagen.desde("follaje"));
        assertEquals("follaje", TipoImagen.FOLLAJE.valor());
    }

    @Test
    void resultadoMapsDatabaseValues() {
        assertEquals(Resultado.SANO, Resultado.desde("sano"));
        assertEquals(Resultado.INFESTADO, Resultado.desde("infestado"));
        assertEquals(Resultado.NO_DETERMINADO, Resultado.desde("no_determinado"));
        assertEquals("no_determinado", Resultado.NO_DETERMINADO.valor());
    }

    @Test
    void estadoSincronizacionMapsDatabaseValues() {
        assertEquals(EstadoSincronizacion.PENDIENTE, EstadoSincronizacion.desde("pendiente"));
        assertEquals(EstadoSincronizacion.SINCRONIZADO, EstadoSincronizacion.desde("sincronizado"));
        assertEquals(EstadoSincronizacion.ERROR, EstadoSincronizacion.desde("error"));
    }

    @Test
    void unknownValuesAreRejectedWithTheirName() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> TipoImagen.desde("raiz"));
        assertEquals("tipo inválido: raiz (valores: corteza, follaje)", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Resultado.desde("SANO"));
        assertThrows(IllegalArgumentException.class, () -> EstadoSincronizacion.desde(""));
    }
}
