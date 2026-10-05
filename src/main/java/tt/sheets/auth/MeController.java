package tt.sheets.auth;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import tt.sheets.user.User;
import tt.sheets.user.UserRepository;

/** GET /me — perfil del usuario autenticado por JWT Bearer. */
@Controller("/me")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @Get
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<MeResponse> me(Authentication authentication) {
        return users.findByUsernameIgnoreCase(authentication.getName())
                .filter(User::enabled)
                .map(u -> HttpResponse.ok(new MeResponse(u.username(), u.displayName())))
                // Token válido pero el usuario ya no existe o está deshabilitado.
                .orElseGet(HttpResponse::unauthorized);
    }
}
