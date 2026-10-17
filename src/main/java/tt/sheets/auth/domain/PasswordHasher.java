package tt.sheets.auth.domain;

/** Puerto de salida: la infraestructura (bcrypt) lo implementa. */
public interface PasswordHasher {

    boolean matches(String rawPassword, String hash);
}
