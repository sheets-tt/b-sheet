package tt.sheets.analisis.domain;

import java.util.UUID;

/** Cuenta que subió un análisis o registró un árbol/zona: solo id y nombre_completo (nunca correo ni hash). */
public record Autor(UUID id, String nombre) {
}
