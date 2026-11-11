package tt.sheets.arbol.infrastructure;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import tt.sheets.arbol.application.ConsultarArboles;
import tt.sheets.arbol.domain.ArbolRepository;

/** Cablea los casos de uso (que no dependen de Micronaut) con sus adaptadores. */
@Factory
class ArbolBeans {

    @Singleton
    ConsultarArboles consultarArboles(ArbolRepository arboles) {
        return new ConsultarArboles(arboles);
    }
}
