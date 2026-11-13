package tt.sheets.cuenta;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Lecturas para login, /me y la validación del JWT. Las escrituras (solo administración de cuentas) van por
 * SQL explícito en JdbcAdministracionRepository, con los permisos por columna que documenta el README.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface CuentaRepository extends GenericRepository<Cuenta, UUID> {

    /** Búsqueda case-insensitive (TT_BKN solo tiene UNIQUE sobre correo, no sobre lower(correo)). */
    @Query("SELECT * FROM cuenta WHERE lower(correo) = lower(:correo)")
    Optional<Cuenta> findByCorreoIgnoreCase(String correo);

    /** Para validar el JWT: la cuenta se identifica por su id (claim "cid"), no solo por el correo. */
    @Query("SELECT * FROM cuenta WHERE id_cuenta = :id")
    Optional<Cuenta> findByIdCuenta(UUID id);
}
