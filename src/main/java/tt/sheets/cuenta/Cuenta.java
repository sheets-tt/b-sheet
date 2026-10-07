package tt.sheets.cuenta;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

import java.time.Instant;

@MappedEntity("cuenta")
public record Cuenta(
        @Id @GeneratedValue @MappedProperty("id_cuenta") Long idCuenta,
        String correo,
        @MappedProperty("contrasena_hash") String contrasenaHash,
        String rol,
        @MappedProperty("estado_cuenta") String estadoCuenta,
        @MappedProperty("fecha_creacion") Instant fechaCreacion,
        @MappedProperty("nombre_completo") String nombreCompleto) {

    public static final String ESTADO_ACTIVA = "ACTIVA";

    /** Solo las cuentas activas pueden iniciar sesión o usar su token. */
    public boolean activa() {
        return ESTADO_ACTIVA.equals(estadoCuenta);
    }
}
