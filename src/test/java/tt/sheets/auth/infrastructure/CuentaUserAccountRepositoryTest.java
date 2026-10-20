package tt.sheets.auth.infrastructure;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tt.sheets.auth.domain.UserAccount;
import tt.sheets.auth.domain.UserAccountRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Adaptador contra la BD de tests (sheets_test), con la cuenta demo de db/demo. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CuentaUserAccountRepositoryTest {

    private static final String DEMO_HASH = "$2a$10$z9OACnNPUJvH25YzOGWusur21etn5BNxNX08wpeDclCjvvmUGLEFm";

    private ApplicationContext context;
    private UserAccountRepository accounts;

    @BeforeAll
    void start() {
        context = ApplicationContext.run("test");
        accounts = context.getBean(UserAccountRepository.class);
    }

    @AfterAll
    void stop() {
        context.close();
    }

    @Test
    void findsActiveAccountByEmailIgnoringCase() {
        assertEquals(Optional.of(new UserAccount("demo@sheets.tt", DEMO_HASH, true)),
                accounts.findByUsername("Demo@Sheets.TT"));
    }
}
