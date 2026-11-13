package tt.sheets.auth.infrastructure;

import com.nimbusds.jwt.JWT;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.RolesFinder;
import io.micronaut.security.token.config.TokenConfiguration;
import io.micronaut.security.token.jwt.validator.DefaultJwtAuthenticationFactory;
import jakarta.inject.Singleton;
import tt.sheets.cuenta.Cuenta;
import tt.sheets.cuenta.CuentaRepository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Un JWT bien firmado y vigente solo autentica si su cuenta (claim {@value #CLAIM_CUENTA}, el id) sigue existiendo,
 * activa, con el mismo rol y con el mismo correo que el {@code sub}. Así, desactivar una cuenta, cambiarle el rol o
 * el correo corta sus tokens emitidos de inmediato, en todos los endpoints, sin esperar a que expiren (1 h), y un
 * token viejo nunca vale por otra cuenta que después tome ese correo. Si no, el token cuenta como inválido: 401 en
 * lo protegido, y el login sigue funcionando. Los tokens sin {@value #CLAIM_CUENTA} (emitidos antes de este
 * cambio) también se rechazan: basta con volver a iniciar sesión.
 */
@Singleton
@Replaces(DefaultJwtAuthenticationFactory.class)
public class CuentaVigenteJwtAuthenticationFactory extends DefaultJwtAuthenticationFactory {

    /** Claim con el id de la cuenta (UUID como texto). */
    public static final String CLAIM_CUENTA = "cid";

    private final CuentaRepository cuentas;

    CuentaVigenteJwtAuthenticationFactory(TokenConfiguration tokenConfiguration, RolesFinder rolesFinder,
                                          CuentaRepository cuentas) {
        super(tokenConfiguration, rolesFinder);
        this.cuentas = cuentas;
    }

    @Override
    public Optional<Authentication> createAuthentication(JWT token) {
        return super.createAuthentication(token).filter(this::vigente);
    }

    /** El id de la cuenta del token, si lo trae y es un UUID. */
    public static Optional<UUID> idCuenta(Authentication authentication) {
        if (!(authentication.getAttributes().get(CLAIM_CUENTA) instanceof String texto)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(texto));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private boolean vigente(Authentication authentication) {
        return idCuenta(authentication)
                .flatMap(cuentas::findByIdCuenta)
                .filter(Cuenta::activa)
                .filter(c -> c.correo().equalsIgnoreCase(authentication.getName()))
                .map(c -> Set.of(c.rolCuenta().name()).equals(Set.copyOf(authentication.getRoles())))
                .orElse(false);
    }
}
