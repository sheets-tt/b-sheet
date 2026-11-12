package tt.sheets.estadistica.infrastructure;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import tt.sheets.estadistica.application.ConsultarEstadisticas;
import tt.sheets.estadistica.domain.EstadisticasRepository;

/** Cablea los casos de uso (que no dependen de Micronaut) con sus adaptadores. */
@Factory
class EstadisticasBeans {

    @Singleton
    ConsultarEstadisticas consultarEstadisticas(EstadisticasRepository estadisticas) {
        return new ConsultarEstadisticas(estadisticas);
    }
}
