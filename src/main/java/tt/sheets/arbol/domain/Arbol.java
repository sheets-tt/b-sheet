package tt.sheets.arbol.domain;

import tt.sheets.analisis.domain.UltimoAnalisis;

import java.time.Instant;
import java.util.UUID;

/** Árbol registrado en campo (tabla arbol de TT_BKN), independiente de cómo se persista. */
public record Arbol(UUID id, String nombreEspecifico, String especie, Double latitud, Double longitud,
                    Instant fechaRegistro, String descripcion, UltimoAnalisis ultimoAnalisis) {
}
