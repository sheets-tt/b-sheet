package tt.sheets.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;

import java.util.List;

/** Respuesta de POST /auth/login según API_CONTRACT.md (claves snake_case; "roles" siempre presente aunque esté vacío). */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Serdeable(naming = SnakeCaseStrategy.class)
public record LoginResponse(String accessToken, String tokenType, Integer expiresIn,
                            String username, List<String> roles) {
}
