package tt.sheets.compartido.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import jakarta.inject.Singleton;

import java.time.Clock;

/** ¿Existe analisis.id_cuenta? (Ver {@link DetectorColumnaOpcional}). */
@Singleton
public class ColumnaAutorAnalisis extends DetectorColumnaOpcional {

    public ColumnaAutorAnalisis(JdbcOperations jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    ColumnaAutorAnalisis(JdbcOperations jdbc, Clock reloj) {
        super(
                jdbc,
                reloj,
                "analisis",
                "id_cuenta",
                "analisis.id_cuenta no existe: se responde autor = null y seguimiento vacío (se vuelve a "
                        + "revisar cada {} min). Aplicar en la BD de TT_BKN "
                        + "docker/postgres/migraciones/2026-11-17_analisis_id_cuenta.sql",
                "analisis.id_cuenta ya existe: desde ahora se responde el autor de cada análisis");
    }
}
