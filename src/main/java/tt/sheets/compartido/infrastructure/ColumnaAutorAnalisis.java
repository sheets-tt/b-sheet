package tt.sheets.compartido.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import jakarta.inject.Singleton;

/** ¿Existe analisis.id_cuenta? (Ver {@link DetectorColumnaOpcional}). */
@Singleton
public class ColumnaAutorAnalisis extends DetectorColumnaOpcional {

    public ColumnaAutorAnalisis(JdbcOperations jdbc) {
        super(
                jdbc,
                "analisis",
                "id_cuenta",
                "analisis.id_cuenta no existe: se responde autor = null y seguimiento vacío. "
                        + "Aplicar en la BD de TT_BKN docker/postgres/migraciones/2026-11-17_analisis_id_cuenta.sql");
    }
}
