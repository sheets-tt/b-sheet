package tt.sheets.administracion.application;

import org.junit.jupiter.api.Test;
import tt.sheets.administracion.domain.AdministracionRepository;
import tt.sheets.administracion.domain.CuentaResumen;
import tt.sheets.administracion.domain.DispositivoResumen;
import tt.sheets.cuenta.Rol;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Caso de uso puro: el puerto se sustituye por un doble en memoria. */
class ConsultarAdministracionTest {

    private static final CuentaResumen ADMIN = new CuentaResumen(UUID.randomUUID(), "admin@sheets.tt",
            "Administrador", Rol.ADMINISTRADOR, true, Instant.parse("2026-10-01T00:00:00Z"));
    private static final DispositivoResumen PIXEL = new DispositivoResumen(UUID.randomUUID(), "pixel",
            "Pixel", "Activo", Instant.parse("2026-10-01T00:00:00Z"), "admin@sheets.tt");

    private final ConsultarAdministracion consultar = new ConsultarAdministracion(new AdministracionRepository() {
        @Override
        public List<CuentaResumen> listarCuentas() {
            return List.of(ADMIN);
        }

        @Override
        public List<DispositivoResumen> listarDispositivos() {
            return List.of(PIXEL);
        }
    });

    @Test
    void cuentasReturnsEveryAccount() {
        assertEquals(List.of(ADMIN), consultar.cuentas());
    }

    @Test
    void dispositivosReturnsEveryDevice() {
        assertEquals(List.of(PIXEL), consultar.dispositivos());
    }
}
