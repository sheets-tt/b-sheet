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
import tt.sheets.cuenta.Cuenta;
import tt.sheets.cuenta.CuentaRepository;

import java.util.List;
import java.util.Optional;

/** POST /auth/login — valida credenciales (bcrypt contra la tabla cuenta) y emite un JWT HS256 con el rol. */
@Controller("/auth")
@Secured(SecurityRule.IS_ANONYMOUS)
public class AuthController {

    private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);

    private final CuentaRepository cuentas;
    private final PasswordVerifier passwords;
    private final AccessRefreshTokenGenerator tokens;

    public AuthController(CuentaRepository cuentas, PasswordVerifier passwords, AccessRefreshTokenGenerator tokens) {
        this.cuentas = cuentas;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Post("/login")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<?> login(@Body @Valid LoginRequest request) {
        // El campo "username" del contrato es el correo de la cuenta.
        Optional<Cuenta> cuenta = cuentas.findByCorreoIgnoreCase(request.username().trim())
                .filter(Cuenta::activa);
        boolean ok = passwords.matches(request.password(), cuenta.map(Cuenta::contrasenaHash).orElse(null));
        if (!ok) {
            LOG.info("login rechazado");
            return HttpResponse.unauthorized().body(new ErrorResponse("Credenciales inválidas"));
        }
        String username = cuenta.get().correo();
        List<String> roles = List.of(cuenta.get().rol());
        return tokens.generate(Authentication.build(username, roles))
                .<HttpResponse<?>>map(t -> HttpResponse.ok(new LoginResponse(
                        t.getAccessToken(), "Bearer", t.getExpiresIn(), username, roles)))
                .orElseGet(() -> {
                    LOG.error("no se pudo generar el JWT (¿JWT_SECRET configurado?)");
                    return HttpResponse.serverError(new ErrorResponse("No se pudo generar el token"));
                });
    }
}
