package tt.sheets.zona.domain;

import tt.sheets.analisis.domain.UltimoAnalisis;

import java.time.Instant;
import java.util.UUID;

/** Zona circular de follaje (tabla zona_follaje de TT_BKN), independiente de cómo se persista. */
public record ZonaFollaje(UUID id, String nombreZona, Double latitudCentro, Double longitudCentro,
                          Double radioMetros, Instant fechaRegistro, String descripcion,
                          UltimoAnalisis ultimoAnalisis) {
}
