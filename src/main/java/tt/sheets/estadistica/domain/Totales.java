package tt.sheets.estadistica.domain;

/** Conteos generales. "ConInfestacion" = con al menos un análisis cuyo resultado es infestado. */
public record Totales(long arboles, long zonas, long analisis, long dispositivos,
                      long arbolesConInfestacion, long zonasConInfestacion) {
}
