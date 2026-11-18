package tt.sheets.analisis.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Imagen capturada en campo y su clasificación. Corteza -> idArbol; follaje -> idZona (chk_arbol_o_zona).
 * {@code cromatico} es null si el análisis no tiene fila en analisis_cromatico. {@code autor} es null si no se
 * sabe quién lo subió (análisis anterior a analisis.id_cuenta, o columna aún no migrada).
 */
public record Analisis(UUID id, String rutaEvidencia, Double latitudCaptura, Double longitudCaptura,
                       Instant fechaCaptura, Instant fechaSincronizacion, EstadoSincronizacion estadoSincronizacion,
                       TipoImagen tipoImagen, Resultado resultadoClasificacion, Double confianza,
                       String observaciones, UUID idArbol, UUID idZona, Cromatico cromatico,
                       Autor autor) {
}
