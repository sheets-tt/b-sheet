package tt.sheets.analisis.domain;

import java.time.Instant;
import java.util.UUID;

/** Criterios de búsqueda; null = sin filtrar por ese campo. Fechas inclusivas sobre fecha_captura. */
public record FiltroAnalisis(TipoImagen tipo, Resultado resultado, EstadoSincronizacion estado,
                             Instant desde, Instant hasta, UUID idArbol, UUID idZona, int limite) {
}
