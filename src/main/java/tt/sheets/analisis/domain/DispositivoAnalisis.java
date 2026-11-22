package tt.sheets.analisis.domain;

import java.util.UUID;

/** Dispositivo móvil desde el que se capturó un análisis ({@code codigo}: prefijo corto para distinguir modelos iguales). */
public record DispositivoAnalisis(UUID id, String nombre, String codigo) {
}
