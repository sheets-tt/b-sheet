package tt.sheets.analisis.domain;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de salida (solo lectura): autores distintos de los análisis de un árbol o zona, de la captura
 * más reciente a la más antigua. Los análisis sin autor (anteriores a analisis.id_cuenta) no cuentan.
 */
public interface SeguimientoRepository {

    List<Seguidor> deArbol(UUID idArbol);

    List<Seguidor> deZona(UUID idZona);
}
