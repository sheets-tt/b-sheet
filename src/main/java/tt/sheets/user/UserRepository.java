package tt.sheets.user;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface UserRepository extends CrudRepository<User, Long> {

    /** Búsqueda case-insensitive (usa el índice único sobre lower(username)). */
    @Query("SELECT * FROM users WHERE lower(username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(String username);
}
