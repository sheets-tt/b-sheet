package tt.sheets.zona.infrastructure;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import tt.sheets.zona.application.ConsultarZonas;
import tt.sheets.zona.domain.ZonaFollajeRepository;

/** Cablea los casos de uso (que no dependen de Micronaut) con sus adaptadores. */
@Factory
class ZonaBeans {

    @Singleton
    ConsultarZonas consultarZonas(ZonaFollajeRepository zonas) {
        return new ConsultarZonas(zonas);
    }
}
