package tt.sheets.administracion.infrastructure;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import tt.sheets.administracion.application.ConsultarAdministracion;
import tt.sheets.administracion.domain.AdministracionRepository;

/** Cablea los casos de uso (que no dependen de Micronaut) con sus adaptadores. */
@Factory
class AdministracionBeans {

    @Singleton
    ConsultarAdministracion consultarAdministracion(AdministracionRepository administracion) {
        return new ConsultarAdministracion(administracion);
    }
}
