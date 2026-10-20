package tt.sheets.auth.infrastructure;

import jakarta.inject.Singleton;
import tt.sheets.auth.domain.UserAccount;
import tt.sheets.auth.domain.UserAccountRepository;
import tt.sheets.cuenta.CuentaRepository;

import java.util.Optional;

/** Adaptador: implementa el puerto del dominio con la tabla cuenta (Micronaut Data + pool Hikari). */
@Singleton
class CuentaUserAccountRepository implements UserAccountRepository {

    private final CuentaRepository cuentas;

    CuentaUserAccountRepository(CuentaRepository cuentas) {
        this.cuentas = cuentas;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return Optional.empty(); // TODO(green)
    }
}
