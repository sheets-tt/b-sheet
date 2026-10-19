package tt.sheets.auth.application;

import tt.sheets.auth.domain.AuthenticatedUser;
import tt.sheets.auth.domain.PasswordHasher;
import tt.sheets.auth.domain.UserAccountRepository;

import java.util.List;
import java.util.Optional;

/** Caso de uso: valida credenciales contra los puertos del dominio. */
public class AuthenticateUser {

    private final UserAccountRepository accounts;
    private final PasswordHasher passwords;

    public AuthenticateUser(UserAccountRepository accounts, PasswordHasher passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    public Optional<AuthenticatedUser> execute(String username, String password) {
        return accounts.findByUsername(username)
                .filter(account -> passwords.matches(password, account.passwordHash()))
                .map(account -> new AuthenticatedUser(account.username(), List.of()));
    }
}
