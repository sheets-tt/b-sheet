package tt.sheets.administracion.infrastructure;

import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import jakarta.inject.Singleton;
import tt.sheets.administracion.domain.AdministracionRepository;
import tt.sheets.administracion.domain.CorreoEnUsoException;
import tt.sheets.administracion.domain.CuentaResumen;
import tt.sheets.administracion.domain.DatosCuenta;
import tt.sheets.administracion.domain.DispositivoResumen;
import tt.sheets.administracion.domain.GestionCuentasRepository;
import tt.sheets.administracion.domain.UltimoAdministradorException;
import tt.sheets.compartido.infrastructure.Sql;
import tt.sheets.cuenta.Cuenta;
import tt.sheets.cuenta.Rol;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador SQL. Los SELECT y RETURNING nombran columna por columna: contrasena_hash y token_dispositivo
 * nunca salen de la BD por este camino. Las escrituras tocan solo las columnas que el README pide otorgar
 * al usuario de producción (GRANT INSERT/UPDATE por columna sobre cuenta).
 */
@Singleton
class JdbcAdministracionRepository implements AdministracionRepository, GestionCuentasRepository {

    private static final String COLUMNAS_CUENTA = "id_cuenta, correo, nombre_completo, rol, estado_cuenta, fecha_creacion";

    private static final String CUENTAS = "SELECT " + COLUMNAS_CUENTA + " FROM cuenta ORDER BY lower(correo)";

    private static final String CUENTA = "SELECT " + COLUMNAS_CUENTA + " FROM cuenta WHERE id_cuenta = ?";

    private static final String CORREO_EN_USO = "SELECT 1 FROM cuenta WHERE lower(correo) = lower(?) LIMIT 1";

    private static final String CORREO_EN_USO_POR_OTRA =
            "SELECT 1 FROM cuenta WHERE lower(correo) = lower(?) AND id_cuenta <> ? LIMIT 1";

    // id generado en Java: no depende de gen_random_uuid() (pgcrypto / PG 13+) en la BD de TT_BKN.
    private static final String CREAR = """
            INSERT INTO cuenta (id_cuenta, correo, contrasena_hash, rol, estado_cuenta, fecha_creacion, nombre_completo)
            VALUES (?, ?, ?, ?, ?, now(), ?)
            RETURNING\s""" + COLUMNAS_CUENTA;

    private static final String ACTUALIZAR =
            "UPDATE cuenta SET correo = ?, nombre_completo = ?, rol = ? WHERE id_cuenta = ?";

    private static final String ACTUALIZAR_CON_CONTRASENA =
            "UPDATE cuenta SET correo = ?, nombre_completo = ?, rol = ?, contrasena_hash = ? WHERE id_cuenta = ?";

    private static final String CAMBIAR_ESTADO = "UPDATE cuenta SET estado_cuenta = ? WHERE id_cuenta = ?";

    private static final String RETORNO = " RETURNING " + COLUMNAS_CUENTA;

    /**
     * Para los cambios que pueden quitarle a una cuenta lo de administrador activo (desactivarla o cambiarle el
     * rol): bloquea las filas de los administradores activos y solo aplica el cambio si la cuenta no lo es o si
     * queda otro. Todo en una sentencia: si dos administradores se desactivan a la vez, el segundo espera al
     * primero, vuelve a leer y no actualiza nada (0 filas → 409). Bloquear con FOR UPDATE solo pide el UPDATE por
     * columna que ya documenta el README.
     */
    private static final String ADMINISTRADORES_BLOQUEADOS = """
            WITH administradores AS (
                SELECT id_cuenta FROM cuenta
                WHERE rol = 'Administrador' AND estado_cuenta = 'Activa'
                ORDER BY id_cuenta
                FOR UPDATE
            )
            """;

    // Cadena normal y no text block: un text block quitaría el espacio inicial y quedaría "?AND", que
    // PostgreSQL 17+ rechaza ("trailing junk after parameter").
    private static final String QUEDA_OTRO_ADMINISTRADOR =
            " AND (NOT (rol = 'Administrador' AND estado_cuenta = 'Activa')"
                    + " OR EXISTS (SELECT 1 FROM administradores a WHERE a.id_cuenta <> cuenta.id_cuenta))";

    private static String simple(String update) {
        return update + RETORNO;
    }

    private static String protegido(String update) {
        return ADMINISTRADORES_BLOQUEADOS + update + QUEDA_OTRO_ADMINISTRADOR + RETORNO;
    }

    /** SQLState de PostgreSQL para unique_violation. */
    private static final String VIOLACION_UNICA = "23505";

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
        return Sql.lista(jdbc, CUENTAS, List.of(), JdbcAdministracionRepository::cuenta);
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

    @Override
    public Optional<CuentaResumen> buscarCuenta(UUID id) {
        return primera(CUENTA, id);
    }

    @Override
    public boolean correoEnUso(String correo, UUID excepto) {
        List<Integer> filas = excepto == null
                ? Sql.lista(jdbc, CORREO_EN_USO, List.of(correo), rs -> 1)
                : Sql.lista(jdbc, CORREO_EN_USO_POR_OTRA, List.of(correo, excepto), rs -> 1);
        return !filas.isEmpty();
    }

    @Override
    public CuentaResumen crearCuenta(UUID id, DatosCuenta datos, String hashContrasena) {
        return primera(CREAR, id, datos.correo(), hashContrasena, datos.rol().valorBd(), Cuenta.ESTADO_ACTIVA,
                datos.nombreCompleto()).orElseThrow();
    }

    @Override
    public Optional<CuentaResumen> actualizarCuenta(UUID id, DatosCuenta datos, String hashContrasena) {
        String update = hashContrasena == null ? ACTUALIZAR : ACTUALIZAR_CON_CONTRASENA;
        Object[] parametros = hashContrasena == null
                ? new Object[] {datos.correo(), datos.nombreCompleto(), datos.rol().valorBd(), id}
                : new Object[] {datos.correo(), datos.nombreCompleto(), datos.rol().valorBd(), hashContrasena, id};
        return datos.rol() == Rol.ADMINISTRADOR
                ? primera(simple(update), parametros)
                : sinDejarSinAdministradores(id, primera(protegido(update), parametros));
    }

    @Override
    public Optional<CuentaResumen> cambiarEstado(UUID id, boolean activa) {
        return activa
                ? primera(simple(CAMBIAR_ESTADO), Cuenta.ESTADO_ACTIVA, id)
                : sinDejarSinAdministradores(id, primera(protegido(CAMBIAR_ESTADO), Cuenta.ESTADO_DESACTIVADA, id));
    }

    /** 0 filas en un cambio protegido: o la cuenta no existe (vacío → 404) o era el último administrador activo. */
    private Optional<CuentaResumen> sinDejarSinAdministradores(UUID id, Optional<CuentaResumen> actualizada) {
        if (actualizada.isEmpty() && buscarCuenta(id).isPresent()) {
            throw new UltimoAdministradorException();
        }
        return actualizada;
    }

    /** Una fila (SELECT por id o INSERT/UPDATE ... RETURNING). El UNIQUE de correo se traduce a 409. */
    private Optional<CuentaResumen> primera(String sql, Object... parametros) {
        try {
            return Sql.lista(jdbc, sql, Arrays.asList(parametros), JdbcAdministracionRepository::cuenta)
                    .stream().findFirst();
        } catch (DataAccessException e) {
            if (esViolacionUnica(e)) {
                throw new CorreoEnUsoException(); // otra petición ganó la carrera tras correoEnUso
            }
            throw e;
        }
    }

    private static boolean esViolacionUnica(Throwable e) {
        List<Throwable> vistos = new ArrayList<>();
        for (Throwable t = e; t != null && !vistos.contains(t); t = t.getCause()) {
            vistos.add(t);
            if (t instanceof SQLException sql && VIOLACION_UNICA.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static CuentaResumen cuenta(ResultSet rs) throws SQLException {
        return new CuentaResumen(
                rs.getObject("id_cuenta", UUID.class),
                rs.getString("correo"),
                rs.getString("nombre_completo"),
                Rol.desdeBd(rs.getString("rol")),
                Cuenta.ESTADO_ACTIVA.equals(rs.getString("estado_cuenta")),
                Sql.instante(rs, "fecha_creacion"));
    }
}
