package tt.sheets.cuenta;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

import java.time.Instant;
import java.util.UUID;

/** Tabla cuenta de TT_BKN (solo lectura desde b-sheet). */
@MappedEntity("cuenta")
public record Cuenta(
        @Id @MappedProperty("id_cuenta") UUID idCuenta,
        String correo,
        @MappedProperty("contrasena_hash") String contrasenaHash,
        String rol,
        @MappedProperty("estado_cuenta") String estadoCuenta,
        @Nullable @MappedProperty("fecha_creacion") Instant fechaCreacion, // sin NOT NULL en TT_BKN
        @MappedProperty("nombre_completo") String nombreCompleto) {

    /** Valor de TT_BKN; el otro posible es "desactivada". */
    public static final String ESTADO_ACTIVA = "Activa";

    /** Solo las cuentas activas pueden iniciar sesión o usar su token. */
    public boolean activa() {
        return ESTADO_ACTIVA.equals(estadoCuenta);
    }

    public Rol rolCuenta() {
        return Rol.desdeBd(rol);
    }
}
