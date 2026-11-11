package tt.sheets.analisis.domain;

import java.time.Instant;
import java.util.UUID;

/** Resumen del análisis más reciente de un árbol o una zona (para el mapa, sin traer el historial). */
public record UltimoAnalisis(UUID id, Resultado resultado, Instant fechaCaptura) {
}
