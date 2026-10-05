package tt.sheets.user;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

@MappedEntity("users")
public record User(
        @Id @GeneratedValue Long id,
        String username,
        @MappedProperty("password_hash") String passwordHash,
        @MappedProperty("display_name") String displayName,
        boolean enabled) {
}
