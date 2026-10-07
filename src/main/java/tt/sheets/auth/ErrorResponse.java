package tt.sheets.auth;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ErrorResponse(String message) {
}
