package tt.sheets.auth.domain;

import java.util.Optional;

/** Puerto de salida: la infraestructura (JDBC) lo implementa. */
public interface UserAccountRepository {

    Optional<UserAccount> findByUsername(String username);
}
