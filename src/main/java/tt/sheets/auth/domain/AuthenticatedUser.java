package tt.sheets.auth.domain;

import java.util.List;

public record AuthenticatedUser(String username, List<String> roles) {
}
