package tt.sheets.zona.domain;

import tt.sheets.analisis.domain.Autor;
import tt.sheets.analisis.domain.UltimoAnalisis;

import java.time.Instant;
import java.util.UUID;

/** Zona circular de follaje (tabla zona_follaje de TT_BKN), independiente de cómo se persista.
 * {@code registradoPor}: la cuenta que lo dio de alta (id_cuenta), o null si ya no existe. */
public record ZonaFollaje(UUID id, String nombreZona, Double latitudCentro, Double longitudCentro,
                          Double radioMetros, Instant fechaRegistro, String descripcion,
                          UltimoAnalisis ultimoAnalisis, Autor registradoPor) {
}
