package tt.sheets.auth;

import jakarta.inject.Singleton;
import org.mindrot.jbcrypt.BCrypt;

@Singleton
public class PasswordVerifier {

    /** Hash ficticio (cost 10) para igualar tiempos cuando el usuario no existe. */
    private static final String DUMMY_HASH = "$2a$10$z9OACnNPUJvH25YzOGWusur21etn5BNxNX08wpeDclCjvvmUGLEFm";

    public boolean matches(String rawPassword, String hash) {
        try {
            return BCrypt.checkpw(rawPassword, hash != null ? hash : DUMMY_HASH) && hash != null;
        } catch (IllegalArgumentException e) {
            return false; // hash con formato inválido
        }
    }
}
