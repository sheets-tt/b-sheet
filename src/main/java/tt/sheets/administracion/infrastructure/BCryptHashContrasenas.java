package tt.sheets.administracion.infrastructure;

import jakarta.inject.Singleton;
import org.mindrot.jbcrypt.BCrypt;
import tt.sheets.administracion.domain.HashContrasenas;

/** BCrypt cost 10, formato $2a$: el mismo que genera TT_BKN, así la app móvil y la web aceptan la cuenta. */
@Singleton
class BCryptHashContrasenas implements HashContrasenas {

    static final int COSTO = 10;

    @Override
    public String hash(String contrasena) {
        return BCrypt.hashpw(contrasena, BCrypt.gensalt(COSTO));
    }
}
