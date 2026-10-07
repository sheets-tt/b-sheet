package tt.sheets.auth;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import tt.sheets.cuenta.Cuenta;
import tt.sheets.cuenta.CuentaRepository;

/** GET /me — perfil de la cuenta autenticada por JWT Bearer. */
@Controller("/me")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class MeController {

    private final CuentaRepository cuentas;

    public MeController(CuentaRepository cuentas) {
        this.cuentas = cuentas;
    }

    @Get
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<MeResponse> me(Authentication authentication) {
        return cuentas.findByCorreoIgnoreCase(authentication.getName())
                .filter(Cuenta::activa)
                .map(c -> HttpResponse.ok(new MeResponse(c.correo(), c.nombreCompleto())))
                // Token válido pero la cuenta ya no existe o no está activa.
                .orElseGet(HttpResponse::unauthorized);
    }
}
