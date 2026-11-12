package tt.sheets.arbol.application;

import tt.sheets.arbol.domain.Arbol;
import tt.sheets.arbol.domain.ArbolRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Caso de uso: consulta de árboles para la web (solo lectura). */
public class ConsultarArboles {

    private final ArbolRepository arboles;

    public ConsultarArboles(ArbolRepository arboles) {
        this.arboles = arboles;
    }

    public List<Arbol> listar() {
        return arboles.findAll();
    }

    public Optional<Arbol> obtener(UUID id) {
        return arboles.findById(id);
    }
}
