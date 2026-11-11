package tt.sheets.arbol.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida (solo lectura): la infraestructura (JDBC) lo implementa. */
public interface ArbolRepository {

    /** Todos los árboles, del registro más reciente al más antiguo. */
    List<Arbol> findAll();

    Optional<Arbol> findById(UUID id);
}
