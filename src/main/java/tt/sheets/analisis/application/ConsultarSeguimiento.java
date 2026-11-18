package tt.sheets.analisis.application;

import tt.sheets.analisis.domain.Seguidor;
import tt.sheets.analisis.domain.SeguimientoRepository;

import java.util.List;
import java.util.UUID;

/** Caso de uso: quiénes le dan seguimiento a un árbol o zona (solo lectura). */
public class ConsultarSeguimiento {

    private final SeguimientoRepository seguimiento;

    public ConsultarSeguimiento(SeguimientoRepository seguimiento) {
        this.seguimiento = seguimiento;
    }

    public List<Seguidor> deArbol(UUID idArbol) {
        return seguimiento.deArbol(idArbol);
    }

    public List<Seguidor> deZona(UUID idZona) {
        return seguimiento.deZona(idZona);
    }
}
