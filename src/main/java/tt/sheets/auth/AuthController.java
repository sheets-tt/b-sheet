package tt.sheets.auth;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.token.generator.AccessRefreshTokenGenerator;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tt.sheets.user.User;
import tt.sheets.user.UserRepository;

import java.util.List;
import java.util.Optional;

/** POST /auth/login — valida credenciales (bcrypt contra tabla users) y emite un JWT HS256. */
@Controller("/auth")
@Secured(SecurityRule.IS_ANONYMOUS)
public class AuthController {

    private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository users;
    private final PasswordVerifier passwords;
    private final AccessRefreshTokenGenerator tokens;

    public AuthController(UserRepository users, PasswordVerifier passwords, AccessRefreshTokenGenerator tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Post("/login")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<?> login(@Body @Valid LoginRequest request) {
        Optional<User> user = users.findByUsernameIgnoreCase(request.username().trim())
                .filter(User::enabled);
        boolean ok = passwords.matches(request.password(), user.map(User::passwordHash).orElse(null));
        if (!ok) {
            LOG.info("login rechazado");
            return HttpResponse.unauthorized().body(new ErrorResponse("Credenciales inválidas"));
        }
        String username = user.get().username();
        List<String> roles = List.of();
        return tokens.generate(Authentication.build(username, roles))
                .<HttpResponse<?>>map(t -> HttpResponse.ok(new LoginResponse(
                        t.getAccessToken(), "Bearer", t.getExpiresIn(), username, roles)))
                .orElseGet(() -> {
                    LOG.error("no se pudo generar el JWT (¿JWT_SECRET configurado?)");
                    return HttpResponse.serverError(new ErrorResponse("No se pudo generar el token"));
                });
    }
}
