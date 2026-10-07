package tt.sheets.cuenta;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface CuentaRepository extends CrudRepository<Cuenta, Long> {

    /** Búsqueda case-insensitive (usa el índice único sobre lower(correo)). */
    @Query("SELECT * FROM cuenta WHERE lower(correo) = lower(:correo)")
    Optional<Cuenta> findByCorreoIgnoreCase(String correo);
}
