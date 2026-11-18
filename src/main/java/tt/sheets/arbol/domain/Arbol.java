package tt.sheets.arbol.domain;

import tt.sheets.analisis.domain.Autor;
import tt.sheets.analisis.domain.UltimoAnalisis;

import java.time.Instant;
import java.util.UUID;

/** Árbol registrado en campo (tabla arbol de TT_BKN), independiente de cómo se persista.
 * {@code registradoPor}: la cuenta que lo dio de alta (id_cuenta), o null si ya no existe. */
public record Arbol(UUID id, String nombreEspecifico, String especie, Double latitud, Double longitud,
                    Instant fechaRegistro, String descripcion, UltimoAnalisis ultimoAnalisis, Autor registradoPor) {
}
