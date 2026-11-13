package tt.sheets.cuenta;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

import java.time.Instant;
import java.util.UUID;

/** Tabla cuenta de TT_BKN. b-sheet la lee aquí; las altas y cambios de administración van por JdbcAdministracionRepository. */
@MappedEntity("cuenta")
public record Cuenta(
        @Id @MappedProperty("id_cuenta") UUID idCuenta,
        String correo,
        @MappedProperty("contrasena_hash") String contrasenaHash,
        String rol,
        @MappedProperty("estado_cuenta") String estadoCuenta,
        @Nullable @MappedProperty("fecha_creacion") Instant fechaCreacion, // sin NOT NULL en TT_BKN
        @MappedProperty("nombre_completo") String nombreCompleto) {

    /** Valores de estado_cuenta en TT_BKN (los exige su CHECK). */
    public static final String ESTADO_ACTIVA = "Activa";
    public static final String ESTADO_DESACTIVADA = "desactivada";

    /** Solo las cuentas activas pueden iniciar sesión o usar su token. */
    public boolean activa() {
        return ESTADO_ACTIVA.equals(estadoCuenta);
    }

    public Rol rolCuenta() {
        return Rol.desdeBd(rol);
    }
}
