package tt.sheets.auth.domain;

/** Cuenta tal como la necesita la autenticación, independiente de cómo se persista. */
public record UserAccount(String username, String passwordHash, boolean enabled) {
}
