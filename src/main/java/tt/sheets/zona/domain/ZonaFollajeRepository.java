package tt.sheets.zona.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida (solo lectura): la infraestructura (JDBC) lo implementa. */
public interface ZonaFollajeRepository {

    /** Todas las zonas, del registro más reciente al más antiguo. */
    List<ZonaFollaje> findAll();

    Optional<ZonaFollaje> findById(UUID id);
}
