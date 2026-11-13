package tt.sheets.administracion;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import tt.sheets.administracion.application.ConsultarAdministracion;

import java.util.List;

/** GET /cuentas y GET /dispositivos — solo para el rol ADMINISTRADOR (403 para los demás). Altas y cambios: GestionCuentasController. */
@Controller
@Secured("ADMINISTRADOR")
public class AdministracionController {

    private final ConsultarAdministracion consultar;

    public AdministracionController(ConsultarAdministracion consultar) {
        this.consultar = consultar;
    }

    @Get("/cuentas")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public List<CuentaResumenResponse> cuentas() {
        return consultar.cuentas().stream().map(CuentaResumenResponse::from).toList();
    }

    @Get("/dispositivos")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public List<DispositivoResumenResponse> dispositivos() {
        return consultar.dispositivos().stream().map(DispositivoResumenResponse::from).toList();
    }
}
