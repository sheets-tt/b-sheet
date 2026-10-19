package tt.sheets.auth.application;

import org.junit.jupiter.api.Test;
import tt.sheets.auth.domain.AuthenticatedUser;
import tt.sheets.auth.domain.PasswordHasher;
import tt.sheets.auth.domain.UserAccount;
import tt.sheets.auth.domain.UserAccountRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Caso de uso puro: sin Micronaut ni BD, los puertos se sustituyen por dobles en memoria. */
class AuthenticateUserTest {

    private static final UserAccount DEMO = new UserAccount("demo@sheets.tt", "hash-demo", true);

    private final UserAccountRepository accounts = username ->
            DEMO.username().equalsIgnoreCase(username) ? Optional.of(DEMO) : Optional.empty();
    private final PasswordHasher passwords = (raw, hash) -> "Demo1234!".equals(raw) && "hash-demo".equals(hash);

    private final AuthenticateUser authenticate = new AuthenticateUser(accounts, passwords);

    @Test
    void validCredentialsReturnAuthenticatedUser() {
        Optional<AuthenticatedUser> result = authenticate.execute("demo@sheets.tt", "Demo1234!");

        assertEquals(Optional.of(new AuthenticatedUser("demo@sheets.tt", List.of())), result);
    }
}
