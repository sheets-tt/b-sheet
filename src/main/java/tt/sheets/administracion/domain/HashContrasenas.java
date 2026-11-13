package tt.sheets.administracion.domain;

/** Puerto de salida: la infraestructura (bcrypt, como TT_BKN) lo implementa. */
public interface HashContrasenas {

    String hash(String contrasena);
}
