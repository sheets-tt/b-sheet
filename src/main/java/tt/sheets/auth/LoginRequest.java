package tt.sheets.auth;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
