package tt.sheets.auth;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record MeResponse(String username, String displayName) {
}
