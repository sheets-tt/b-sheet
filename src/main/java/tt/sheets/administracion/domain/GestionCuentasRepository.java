package tt.sheets.administracion.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para que un administrador dé de alta y modifique cuentas (RN-01, RF-26). Las cuentas no
 * se borran: se desactivan. Ningún método recibe ni devuelve el hash fuera de {@code hashContrasena}.
 */
public interface GestionCuentasRepository {

    Optional<CuentaResumen> buscarCuenta(UUID id);

    /** ¿Hay otra cuenta (distinta de {@code excepto}, que puede ser null) con ese correo, sin importar mayúsculas? */
    boolean correoEnUso(String correo, UUID excepto);

    /** Inserta una cuenta activa. Lanza {@link CorreoEnUsoException} si el correo ya existe. */
    CuentaResumen crearCuenta(UUID id, DatosCuenta datos, String hashContrasena);

    /**
     * {@code hashContrasena} null conserva la contraseña actual. Vacío si la cuenta no existe. Lanza
     * {@link UltimoAdministradorException} si le quitaría el rol al último administrador activo.
     */
    Optional<CuentaResumen> actualizarCuenta(UUID id, DatosCuenta datos, String hashContrasena);

    /** Vacío si la cuenta no existe. Lanza {@link UltimoAdministradorException} si desactivaría al último administrador activo. */
    Optional<CuentaResumen> cambiarEstado(UUID id, boolean activa);
}
