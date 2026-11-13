package tt.sheets.administracion.application;

import org.junit.jupiter.api.Test;
import tt.sheets.administracion.domain.ContrasenaInvalidaException;
import tt.sheets.administracion.domain.CorreoEnUsoException;
import tt.sheets.administracion.domain.CuentaNoEncontradaException;
import tt.sheets.administracion.domain.CuentaPropiaException;
import tt.sheets.administracion.domain.CuentaResumen;
import tt.sheets.administracion.domain.DatosCuenta;
import tt.sheets.administracion.domain.GestionCuentasRepository;
import tt.sheets.cuenta.Rol;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Caso de uso puro: repositorio en memoria y un "hash" reconocible. */
class GestionarCuentasTest {

    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID EXPERTO = UUID.randomUUID();

    private final Map<UUID, CuentaResumen> filas = new HashMap<>();
    private final Map<UUID, String> hashesGuardados = new HashMap<>();
    private final GestionarCuentas gestionar = new GestionarCuentas(new EnMemoria(), c -> "hash:" + c);

    GestionarCuentasTest() {
        filas.put(ADMIN, new CuentaResumen(ADMIN, "admin@sheets.tt", "Admin", Rol.ADMINISTRADOR, true, Instant.EPOCH));
        filas.put(EXPERTO, new CuentaResumen(EXPERTO, "Experto@Sheets.tt", "Experto", Rol.EXPERTO_CAMPO, true, Instant.EPOCH));
        hashesGuardados.put(EXPERTO, "hash:original");
    }

    @Test
    void creaUnaCuentaActivaConElCorreoNormalizadoYLaContrasenaHasheada() {
        CuentaResumen nueva = gestionar.crear(new DatosCuenta("  Nueva@Sheets.TT ", " Nueva Persona ", Rol.EXPERTO_CAMPO),
                "Secreta123");
        assertEquals("nueva@sheets.tt", nueva.correo());
        assertEquals("Nueva Persona", nueva.nombreCompleto());
        assertTrue(nueva.activa());
        assertEquals("hash:Secreta123", hashesGuardados.get(nueva.id()));
    }

    @Test
    void noCreaUnCorreoRepetidoAunqueCambienLasMayusculas() {
        assertThrows(CorreoEnUsoException.class, () -> gestionar.crear(
                new DatosCuenta("EXPERTO@sheets.tt", "Otro", Rol.EXPERTO_CAMPO), "Secreta123"));
    }

    @Test
    void validaLaContrasenaAntesDeTocarElRepositorio() {
        DatosCuenta datos = new DatosCuenta("x@sheets.tt", "X", Rol.EXPERTO_CAMPO);
        assertThrows(ContrasenaInvalidaException.class, () -> gestionar.crear(datos, null));
        assertThrows(ContrasenaInvalidaException.class, () -> gestionar.crear(datos, "        "));
        assertThrows(ContrasenaInvalidaException.class, () -> gestionar.crear(datos, "corta12"));
        assertThrows(ContrasenaInvalidaException.class, () -> gestionar.crear(datos, "ñ".repeat(37))); // 74 bytes
        assertEquals(2, filas.size());
        gestionar.crear(datos, "ñ".repeat(36)); // exactamente 72 bytes
    }

    @Test
    void editarSinContrasenaConservaLaActual() {
        CuentaResumen editada = gestionar.editar(EXPERTO, new DatosCuenta("experto@sheets.tt", "Renombrado",
                Rol.EXPERTO_CAMPO), "  ", "admin@sheets.tt");
        assertEquals("Renombrado", editada.nombreCompleto());
        assertEquals("hash:original", hashesGuardados.get(EXPERTO));
    }

    @Test
    void editarConContrasenaLaReemplaza() {
        gestionar.editar(EXPERTO, new DatosCuenta("experto@sheets.tt", "Experto", Rol.EXPERTO_CAMPO), "Nueva12345",
                "admin@sheets.tt");
        assertEquals("hash:Nueva12345", hashesGuardados.get(EXPERTO));
    }

    @Test
    void editarNoPermiteElCorreoDeOtraCuenta() {
        assertThrows(CorreoEnUsoException.class, () -> gestionar.editar(EXPERTO,
                new DatosCuenta("ADMIN@sheets.tt", "Experto", Rol.EXPERTO_CAMPO), null, "admin@sheets.tt"));
    }

    @Test
    void elAdministradorNoPuedeQuitarseElRolNiDesactivarse() {
        assertThrows(CuentaPropiaException.class, () -> gestionar.editar(ADMIN,
                new DatosCuenta("admin@sheets.tt", "Admin", Rol.EXPERTO_CAMPO), null, "Admin@Sheets.TT"));
        assertThrows(CuentaPropiaException.class, () -> gestionar.desactivar(ADMIN, "admin@sheets.tt"));
        assertTrue(filas.get(ADMIN).activa());
        assertEquals(Rol.ADMINISTRADOR, filas.get(ADMIN).rol());
        // Sí puede editar sus otros datos.
        assertEquals("Otro nombre", gestionar.editar(ADMIN, new DatosCuenta("admin@sheets.tt", "Otro nombre",
                Rol.ADMINISTRADOR), null, "admin@sheets.tt").nombreCompleto());
    }

    @Test
    void desactivaYReactivaOtraCuenta() {
        assertFalse(gestionar.desactivar(EXPERTO, "admin@sheets.tt").activa());
        assertTrue(gestionar.activar(EXPERTO).activa());
    }

    @Test
    void unIdInexistenteEsNoEncontrada() {
        UUID nadie = UUID.randomUUID();
        DatosCuenta datos = new DatosCuenta("nadie@sheets.tt", "Nadie", Rol.EXPERTO_CAMPO);
        assertThrows(CuentaNoEncontradaException.class, () -> gestionar.editar(nadie, datos, null, "admin@sheets.tt"));
        assertThrows(CuentaNoEncontradaException.class, () -> gestionar.desactivar(nadie, "admin@sheets.tt"));
        assertThrows(CuentaNoEncontradaException.class, () -> gestionar.activar(nadie));
        assertNull(filas.get(nadie));
    }

    private final class EnMemoria implements GestionCuentasRepository {

        @Override
        public Optional<CuentaResumen> buscarCuenta(UUID id) {
            return Optional.ofNullable(filas.get(id));
        }

        @Override
        public boolean correoEnUso(String correo, UUID excepto) {
            return filas.values().stream().anyMatch(c -> c.correo().equalsIgnoreCase(correo) && !c.id().equals(excepto));
        }

        @Override
        public CuentaResumen crearCuenta(UUID id, DatosCuenta datos, String hash) {
            CuentaResumen c = new CuentaResumen(id, datos.correo(), datos.nombreCompleto(), datos.rol(), true, Instant.now());
            filas.put(id, c);
            hashesGuardados.put(id, hash);
            return c;
        }

        @Override
        public Optional<CuentaResumen> actualizarCuenta(UUID id, DatosCuenta datos, String hash) {
            return buscarCuenta(id).map(a -> {
                CuentaResumen c = new CuentaResumen(id, datos.correo(), datos.nombreCompleto(), datos.rol(), a.activa(),
                        a.fechaCreacion());
                filas.put(id, c);
                if (hash != null) {
                    hashesGuardados.put(id, hash);
                }
                return c;
            });
        }

        @Override
        public Optional<CuentaResumen> cambiarEstado(UUID id, boolean activa) {
            return buscarCuenta(id).map(a -> {
                CuentaResumen c = new CuentaResumen(id, a.correo(), a.nombreCompleto(), a.rol(), activa, a.fechaCreacion());
                filas.put(id, c);
                return c;
            });
        }
    }
}
