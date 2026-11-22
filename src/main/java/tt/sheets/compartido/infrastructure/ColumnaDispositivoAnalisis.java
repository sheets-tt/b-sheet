package tt.sheets.compartido.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import jakarta.inject.Singleton;

import java.time.Clock;

/** ¿Existe analisis.id_dispositivo? (Ver {@link DetectorColumnaOpcional}). */
@Singleton
public class ColumnaDispositivoAnalisis extends DetectorColumnaOpcional {

    public ColumnaDispositivoAnalisis(JdbcOperations jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    ColumnaDispositivoAnalisis(JdbcOperations jdbc, Clock reloj) {
        super(
                jdbc,
                reloj,
                "analisis",
                "id_dispositivo",
                "analisis.id_dispositivo no existe: se responde dispositivo = null (se vuelve a "
                        + "revisar cada {} min). Aplicar en la BD de TT_BKN "
                        + "docker/postgres/migraciones/2026-11-19_analisis_id_dispositivo.sql",
                "analisis.id_dispositivo ya existe: desde ahora se responde el dispositivo de cada análisis");
    }
}
