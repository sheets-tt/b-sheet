package tt.sheets.zona.application;

import tt.sheets.zona.domain.ZonaFollaje;
import tt.sheets.zona.domain.ZonaFollajeRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Caso de uso: consulta de zonas de follaje para la web (solo lectura). */
public class ConsultarZonas {

    private final ZonaFollajeRepository zonas;

    public ConsultarZonas(ZonaFollajeRepository zonas) {
        this.zonas = zonas;
    }

    public List<ZonaFollaje> listar() {
        return zonas.findAll();
    }

    public Optional<ZonaFollaje> obtener(UUID id) {
        return zonas.findById(id);
    }
}
