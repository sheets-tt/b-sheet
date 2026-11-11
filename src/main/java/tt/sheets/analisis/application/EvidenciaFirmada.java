package tt.sheets.analisis.application;

import java.net.URI;
import java.time.Instant;

/** URL de la foto, válida hasta {@code expira}. */
public record EvidenciaFirmada(URI url, Instant expira) {
}
