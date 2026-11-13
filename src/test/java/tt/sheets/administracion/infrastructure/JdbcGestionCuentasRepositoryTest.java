package tt.sheets.administracion.infrastructure;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mindrot.jbcrypt.BCrypt;
import tt.sheets.administracion.domain.CorreoEnUsoException;
import tt.sheets.administracion.domain.CuentaResumen;
import tt.sheets.administracion.domain.DatosCuenta;
import tt.sheets.administracion.domain.GestionCuentasRepository;
import tt.sheets.administracion.domain.HashContrasenas;
import tt.sheets.administracion.domain.UltimoAdministradorException;
import tt.sheets.compartido.infrastructure.Sql;
import tt.sheets.cuenta.Rol;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Escrituras contra la BD de tests (réplica del esquema de TT_BKN). Lo que se crea aquí usa el prefijo
 * jdbc-gestion- y se borra después de cada prueba, para no alterar las 3 cuentas de db/demo.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcGestionCuentasRepositoryTest {

    private static final UUID DEMO = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    /** El único administrador activo de db/demo. */
    private static final UUID ADMIN = UUID.fromString("a0000000-0000-0000-0000-000000000002");

    private ApplicationContext context;
    private GestionCuentasRepository cuentas;
    private JdbcOperations jdbc;

    @BeforeAll
    void start() {
        context = ApplicationContext.run("test");
        cuentas = context.getBean(GestionCuentasRepository.class);
        jdbc = context.getBean(JdbcOperations.class);
        limpiar();
    }

    @AfterEach
    void limpiar() {
        jdbc.execute(c -> c.prepareStatement("DELETE FROM cuenta WHERE correo LIKE 'jdbc-gestion-%'").executeUpdate());
    }

    @AfterAll
    void stop() {
        context.close();
    }

    private String hashGuardado(UUID id) {
        return Sql.lista(jdbc, "SELECT contrasena_hash FROM cuenta WHERE id_cuenta = ?", List.of(id),
                rs -> rs.getString(1)).getFirst();
    }

    @Test
    void creaUnaCuentaActivaConFechaDeCreacion() {
        UUID id = UUID.randomUUID();
        String hash = context.getBean(HashContrasenas.class).hash("Secreta123");
        CuentaResumen creada = cuentas.crearCuenta(id, new DatosCuenta("jdbc-gestion-a@sheets.tt", "A", Rol.ADMINISTRADOR), hash);

        assertEquals(id, creada.id());
        assertEquals(Rol.ADMINISTRADOR, creada.rol());
        assertTrue(creada.activa());
        assertTrue(Duration.between(creada.fechaCreacion(), Instant.now()).abs().toMinutes() < 5);
        assertEquals(creada, cuentas.buscarCuenta(id).orElseThrow());
        // Mismo formato que TT_BKN: $2a$, cost 10.
        assertTrue(hashGuardado(id).startsWith("$2a$10$"), hashGuardado(id));
        assertTrue(BCrypt.checkpw("Secreta123", hashGuardado(id)));
    }

    @Test
    void elUniqueDeCorreoSeTraduceACorreoEnUso() {
        DatosCuenta datos = new DatosCuenta("jdbc-gestion-b@sheets.tt", "B", Rol.EXPERTO_CAMPO);
        cuentas.crearCuenta(UUID.randomUUID(), datos, "$2a$10$x");
        // Sin pasar por correoEnUso (como si otra petición ganara la carrera).
        assertThrows(CorreoEnUsoException.class, () -> cuentas.crearCuenta(UUID.randomUUID(), datos, "$2a$10$x"));
    }

    @Test
    void correoEnUsoIgnoraMayusculasYPuedeExcluirUnaCuenta() {
        assertTrue(cuentas.correoEnUso("DEMO@sheets.tt", null));
        assertFalse(cuentas.correoEnUso("Demo@Sheets.tt", DEMO));
        assertFalse(cuentas.correoEnUso("jdbc-gestion-nadie@sheets.tt", null));
    }

    @Test
    void actualizaDatosYSoloCambiaElHashSiSeDa() {
        UUID id = UUID.randomUUID();
        cuentas.crearCuenta(id, new DatosCuenta("jdbc-gestion-c@sheets.tt", "C", Rol.EXPERTO_CAMPO), "$2a$10$original");

        CuentaResumen editada = cuentas.actualizarCuenta(id,
                new DatosCuenta("jdbc-gestion-c2@sheets.tt", "C dos", Rol.ADMINISTRADOR), null).orElseThrow();
        assertEquals("jdbc-gestion-c2@sheets.tt", editada.correo());
        assertEquals("C dos", editada.nombreCompleto());
        assertEquals(Rol.ADMINISTRADOR, editada.rol());
        assertEquals("$2a$10$original", hashGuardado(id));

        cuentas.actualizarCuenta(id, new DatosCuenta("jdbc-gestion-c2@sheets.tt", "C dos", Rol.ADMINISTRADOR),
                "$2a$10$nuevo").orElseThrow();
        assertEquals("$2a$10$nuevo", hashGuardado(id));
    }

    @Test
    void cambiaElEstadoConLosValoresDeTtBkn() {
        UUID id = UUID.randomUUID();
        cuentas.crearCuenta(id, new DatosCuenta("jdbc-gestion-d@sheets.tt", "D", Rol.EXPERTO_CAMPO), "$2a$10$x");

        assertFalse(cuentas.cambiarEstado(id, false).orElseThrow().activa());
        assertEquals(List.of("desactivada"), Sql.lista(jdbc, "SELECT estado_cuenta FROM cuenta WHERE id_cuenta = ?",
                List.of(id), rs -> rs.getString(1)));
        assertTrue(cuentas.cambiarEstado(id, true).orElseThrow().activa());
    }

    @Test
    void unIdInexistenteNoActualizaNada() {
        UUID nadie = UUID.randomUUID();
        assertTrue(cuentas.buscarCuenta(nadie).isEmpty());
        assertTrue(cuentas.actualizarCuenta(nadie, new DatosCuenta("jdbc-gestion-e@sheets.tt", "E", Rol.EXPERTO_CAMPO),
                null).isEmpty());
        assertTrue(cuentas.cambiarEstado(nadie, false).isEmpty());
    }

    // ---- nunca queda el sistema sin administradores activos ----

    private UUID administrador(String correo) {
        UUID id = UUID.randomUUID();
        cuentas.crearCuenta(id, new DatosCuenta(correo, "Admin", Rol.ADMINISTRADOR),
                context.getBean(HashContrasenas.class).hash("Secreta123"));
        return id;
    }

    @Test
    void noDesactivaNiDegradaAlUltimoAdministradorActivo() {
        assertThrows(UltimoAdministradorException.class, () -> cuentas.cambiarEstado(ADMIN, false));
        assertThrows(UltimoAdministradorException.class, () -> cuentas.actualizarCuenta(ADMIN,
                new DatosCuenta("admin@sheets.tt", "Administrador Demo", Rol.EXPERTO_CAMPO), null));
        CuentaResumen admin = cuentas.buscarCuenta(ADMIN).orElseThrow();
        assertTrue(admin.activa());
        assertEquals(Rol.ADMINISTRADOR, admin.rol());
        // Cambios que no le quitan nada siguen funcionando, y una cuenta que no existe sigue siendo vacío (404).
        assertTrue(cuentas.actualizarCuenta(ADMIN,
                new DatosCuenta("admin@sheets.tt", "Administrador Demo", Rol.ADMINISTRADOR), null).isPresent());
        assertTrue(cuentas.cambiarEstado(UUID.randomUUID(), false).isEmpty());
        assertTrue(cuentas.cambiarEstado(DEMO, false).isPresent()); // no es administrador: sin restricción
        cuentas.cambiarEstado(DEMO, true);
    }

    @Test
    void conOtroAdministradorActivoSiSePuedeDesactivarODegradar() {
        UUID otro = administrador("jdbc-gestion-otro@sheets.tt");
        assertEquals(Rol.EXPERTO_CAMPO, cuentas.actualizarCuenta(otro,
                new DatosCuenta("jdbc-gestion-otro@sheets.tt", "Admin", Rol.EXPERTO_CAMPO), null).orElseThrow().rol());
        UUID tercero = administrador("jdbc-gestion-tercero@sheets.tt");
        assertFalse(cuentas.cambiarEstado(tercero, false).orElseThrow().activa());
        // Una cuenta ya desactivada se puede editar o volver a desactivar aunque solo quede un administrador.
        assertFalse(cuentas.cambiarEstado(tercero, false).orElseThrow().activa());
    }

    @Test
    void dosAdministradoresQueSeDesactivanALaVezNoDejanElSistemaSinAdministradores() throws Exception {
        UUID otro = administrador("jdbc-gestion-carrera@sheets.tt");
        try {
            CountDownLatch salida = new CountDownLatch(1);
            CompletableFuture<Boolean> a = CompletableFuture.supplyAsync(() -> desactivarTrasSalida(salida, ADMIN));
            CompletableFuture<Boolean> b = CompletableFuture.supplyAsync(() -> desactivarTrasSalida(salida, otro));
            salida.countDown();
            boolean okA = a.get(20, TimeUnit.SECONDS);
            boolean okB = b.get(20, TimeUnit.SECONDS);
            assertTrue(okA ^ okB, "exactamente uno debe desactivarse: " + okA + " / " + okB);
            long activos = Sql.lista(jdbc,
                    "SELECT 1 FROM cuenta WHERE rol = 'Administrador' AND estado_cuenta = 'Activa'", List.of(), rs -> 1).size();
            assertEquals(1, activos);
        } finally {
            cuentas.cambiarEstado(ADMIN, true); // deja db/demo como estaba
        }
    }

    private boolean desactivarTrasSalida(CountDownLatch salida, UUID id) {
        try {
            salida.await();
            cuentas.cambiarEstado(id, false);
            return true;
        } catch (UltimoAdministradorException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
