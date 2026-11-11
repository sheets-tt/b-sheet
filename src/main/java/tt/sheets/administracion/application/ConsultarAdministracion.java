package tt.sheets.administracion.application;

import tt.sheets.administracion.domain.AdministracionRepository;
import tt.sheets.administracion.domain.CuentaResumen;
import tt.sheets.administracion.domain.DispositivoResumen;

import java.util.List;

/** Caso de uso: consultas de administración (el control de rol lo hace el controlador con @Secured). */
public class ConsultarAdministracion {

    private final AdministracionRepository administracion;

    public ConsultarAdministracion(AdministracionRepository administracion) {
        this.administracion = administracion;
    }

    public List<CuentaResumen> cuentas() {
        return administracion.listarCuentas();
    }

    public List<DispositivoResumen> dispositivos() {
        return administracion.listarDispositivos();
    }
}
