package tt.sheets.administracion.application;

import tt.sheets.administracion.domain.Contrasenas;
import tt.sheets.administracion.domain.CorreoEnUsoException;
import tt.sheets.administracion.domain.CuentaNoEncontradaException;
import tt.sheets.administracion.domain.CuentaPropiaException;
import tt.sheets.administracion.domain.CuentaResumen;
import tt.sheets.administracion.domain.DatosCuenta;
import tt.sheets.administracion.domain.GestionCuentasRepository;
import tt.sheets.administracion.domain.HashContrasenas;
import tt.sheets.cuenta.Rol;

import java.util.UUID;

/**
 * Caso de uso: un administrador crea, edita, desactiva y reactiva cuentas (RN-01: no hay registro público;
 * RF-26). El rol lo exige el controlador con @Secured; aquí, {@code actor} es el correo del JWT de quien
 * hace el cambio, para impedir que se deje sin acceso (desactivarse o quitarse el rol ADMINISTRADOR).
 */
public class GestionarCuentas {

    private final GestionCuentasRepository cuentas;
    private final HashContrasenas hashes;

    public GestionarCuentas(GestionCuentasRepository cuentas, HashContrasenas hashes) {
        this.cuentas = cuentas;
        this.hashes = hashes;
    }

    public CuentaResumen crear(DatosCuenta datos, String contrasena) {
        Contrasenas.validar(contrasena);
        if (cuentas.correoEnUso(datos.correo(), null)) {
            throw new CorreoEnUsoException();
        }
        return cuentas.crearCuenta(UUID.randomUUID(), datos, hashes.hash(contrasena));
    }

    /** {@code contrasena} null o en blanco conserva la actual. */
    public CuentaResumen editar(UUID id, DatosCuenta datos, String contrasena, String actor) {
        boolean cambiaContrasena = contrasena != null && !contrasena.isBlank();
        if (cambiaContrasena) {
            Contrasenas.validar(contrasena);
        }
        CuentaResumen actual = cuentas.buscarCuenta(id).orElseThrow(CuentaNoEncontradaException::new);
        if (esPropia(actual, actor) && datos.rol() != Rol.ADMINISTRADOR) {
            throw new CuentaPropiaException("No puedes quitarte el rol de administrador");
        }
        if (cuentas.correoEnUso(datos.correo(), id)) {
            throw new CorreoEnUsoException();
        }
        return cuentas.actualizarCuenta(id, datos, cambiaContrasena ? hashes.hash(contrasena) : null)
                .orElseThrow(CuentaNoEncontradaException::new);
    }

    public CuentaResumen desactivar(UUID id, String actor) {
        CuentaResumen actual = cuentas.buscarCuenta(id).orElseThrow(CuentaNoEncontradaException::new);
        if (esPropia(actual, actor)) {
            throw new CuentaPropiaException("No puedes desactivar tu propia cuenta");
        }
        return cuentas.cambiarEstado(id, false).orElseThrow(CuentaNoEncontradaException::new);
    }

    public CuentaResumen activar(UUID id) {
        return cuentas.cambiarEstado(id, true).orElseThrow(CuentaNoEncontradaException::new);
    }

    private static boolean esPropia(CuentaResumen cuenta, String actor) {
        return actor != null && cuenta.correo().equalsIgnoreCase(actor.trim());
    }
}
