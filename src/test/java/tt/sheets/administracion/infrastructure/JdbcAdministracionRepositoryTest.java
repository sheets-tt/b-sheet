package tt.sheets.administracion.infrastructure;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tt.sheets.administracion.domain.AdministracionRepository;
import tt.sheets.administracion.domain.CuentaResumen;
import tt.sheets.administracion.domain.DispositivoResumen;
import tt.sheets.cuenta.Rol;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Adaptador SQL contra db/demo: 3 cuentas y 1 dispositivo asociado a demo@sheets.tt. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAdministracionRepositoryTest {

    private ApplicationContext context;
    private AdministracionRepository administracion;

    @BeforeAll
    void start() {
        context = ApplicationContext.run("test");
        administracion = context.getBean(AdministracionRepository.class);
    }

    @AfterAll
    void stop() {
        context.close();
    }

    @Test
    void listsAccountsByEmailWithRoleAndState() {
        List<CuentaResumen> cuentas = administracion.listarCuentas();
        assertEquals(List.of("admin@sheets.tt", "baja@sheets.tt", "demo@sheets.tt"),
                cuentas.stream().map(CuentaResumen::correo).toList());

        CuentaResumen baja = cuentas.get(1);
        assertEquals(UUID.fromString("a0000000-0000-0000-0000-000000000003"), baja.id());
        assertEquals("Cuenta Desactivada", baja.nombreCompleto());
        assertEquals(Rol.EXPERTO_CAMPO, baja.rol());
        assertEquals(false, baja.activa());
        assertNotNull(baja.fechaCreacion());
        assertEquals(Rol.ADMINISTRADOR, cuentas.get(0).rol());
        assertEquals(true, cuentas.get(0).activa());
    }

    @Test
    void listsDevicesWithTheirCurrentAccount() {
        assertEquals(List.of(new DispositivoResumen(UUID.fromString("d0000000-0000-0000-0000-000000000001"),
                "demo-pixel-7-pro", "Pixel 7 Pro demo", "Activo",
                administracion.listarDispositivos().get(0).fechaRegistro(), "demo@sheets.tt")),
                administracion.listarDispositivos());
        Instant registro = administracion.listarDispositivos().get(0).fechaRegistro();
        assertNotNull(registro);
    }
}
