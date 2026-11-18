package tt.sheets.analisis.domain;

import java.time.Instant;
import java.util.UUID;

/** Persona que le da seguimiento a un árbol o zona: cuántos análisis subió y la captura más reciente. */
public record Seguidor(UUID id, String nombre, int analisis, Instant ultimo) {
}
