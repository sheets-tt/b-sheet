package tt.sheets.administracion.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import jakarta.inject.Singleton;
import tt.sheets.administracion.domain.AdministracionRepository;
import tt.sheets.administracion.domain.CuentaResumen;
import tt.sheets.administracion.domain.DispositivoResumen;
import tt.sheets.compartido.infrastructure.Sql;
import tt.sheets.cuenta.Cuenta;
import tt.sheets.cuenta.Rol;

import java.util.List;
import java.util.UUID;

/**
 * Adaptador SQL. Los SELECT nombran columna por columna: contrasena_hash y token_dispositivo
 * nunca salen de la BD por este camino.
 */
@Singleton
class JdbcAdministracionRepository implements AdministracionRepository {

    private static final String CUENTAS = """
            SELECT id_cuenta, correo, nombre_completo, rol, estado_cuenta, fecha_creacion
            FROM cuenta
            ORDER BY lower(correo)
            """;

    private static final String DISPOSITIVOS = """
            SELECT d.id_dispositivo, d.identificador_unico, d.nombre_dispositivo, d.estado_dispositivo,
                   d.fecha_registro,
                   (SELECT c.correo
                    FROM cuenta_dispositivo cd JOIN cuenta c ON c.id_cuenta = cd.id_cuenta
                    WHERE cd.id_dispositivo = d.id_dispositivo
                      AND cd.fecha_desasociacion IS NULL AND cd.estado_cuenta_dispositivo = 'Activa'
                    ORDER BY cd.fecha_asociacion DESC
                    LIMIT 1) AS cuenta_vigente
            FROM dispositivo d
            ORDER BY d.fecha_registro DESC NULLS LAST, d.identificador_unico
            """;

    private final JdbcOperations jdbc;

    JdbcAdministracionRepository(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CuentaResumen> listarCuentas() {
        return Sql.lista(jdbc, CUENTAS, List.of(), rs -> new CuentaResumen(
                rs.getObject("id_cuenta", UUID.class),
                rs.getString("correo"),
                rs.getString("nombre_completo"),
                Rol.desdeBd(rs.getString("rol")),
                Cuenta.ESTADO_ACTIVA.equals(rs.getString("estado_cuenta")),
                Sql.instante(rs, "fecha_creacion")));
    }

    @Override
    public List<DispositivoResumen> listarDispositivos() {
        return Sql.lista(jdbc, DISPOSITIVOS, List.of(), rs -> new DispositivoResumen(
                rs.getObject("id_dispositivo", UUID.class),
                rs.getString("identificador_unico"),
                rs.getString("nombre_dispositivo"),
                rs.getString("estado_dispositivo"),
                Sql.instante(rs, "fecha_registro"),
                rs.getString("cuenta_vigente")));
    }
}
