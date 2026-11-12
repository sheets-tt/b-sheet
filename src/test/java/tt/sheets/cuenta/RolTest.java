package tt.sheets.cuenta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** La BD de TT_BKN guarda el rol como texto libre; la API expone códigos estables sin espacios. */
class RolTest {

    @Test
    void mapsDatabaseValuesToCodes() {
        assertEquals(Rol.ADMINISTRADOR, Rol.desdeBd("Administrador"));
        assertEquals(Rol.EXPERTO_CAMPO, Rol.desdeBd("Experto de campo"));
    }

    @Test
    void rejectsUnknownValues() {
        assertThrows(IllegalArgumentException.class, () -> Rol.desdeBd("USUARIO"));
    }
}
