package tt.sheets.compartido.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import jakarta.inject.Singleton;

/** ¿Existe analisis.id_dispositivo? (Ver {@link DetectorColumnaOpcional}). */
@Singleton
public class ColumnaDispositivoAnalisis extends DetectorColumnaOpcional {

    public ColumnaDispositivoAnalisis(JdbcOperations jdbc) {
        super(
                jdbc,
                "analisis",
                "id_dispositivo",
                "analisis.id_dispositivo no existe: se responde dispositivo = null. "
                        + "Aplicar en la BD de TT_BKN docker/postgres/migraciones/2026-11-19_analisis_id_dispositivo.sql");
    }
}
