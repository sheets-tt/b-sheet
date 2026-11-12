package tt.sheets.cuenta;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

import java.util.Optional;
import java.util.UUID;

/** Solo lectura: la BD es de TT_BKN y b-sheet entra con un usuario sin permisos de escritura. */
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface CuentaRepository extends GenericRepository<Cuenta, UUID> {

    /** Búsqueda case-insensitive (TT_BKN solo tiene UNIQUE sobre correo, no sobre lower(correo)). */
    @Query("SELECT * FROM cuenta WHERE lower(correo) = lower(:correo)")
    Optional<Cuenta> findByCorreoIgnoreCase(String correo);
}
