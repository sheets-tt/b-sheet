package tt.sheets.administracion.domain;

import java.util.List;

/** Puerto de salida de las consultas de administración; los cambios a cuentas van por GestionCuentasRepository. */
public interface AdministracionRepository {

    /** Todas las cuentas, ordenadas por correo. */
    List<CuentaResumen> listarCuentas();

    /** Todos los dispositivos, del registro más reciente al más antiguo. */
    List<DispositivoResumen> listarDispositivos();
}
