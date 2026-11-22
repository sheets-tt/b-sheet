package tt.sheets.compartido.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * ¿Existe analisis.id_cuenta? La agrega la migración 2026-11-17_analisis_id_cuenta.sql de TT_BKN, que se
 * aplica a mano en producción: b-sheet puede desplegarse antes. Sin la columna, los análisis salen con
 * autor = null y el seguimiento vacío, en vez de fallar con "column does not exist".
 * <p>
 * Se consulta en el primer uso (no al construir el bean: en Lambda los singletons se crean durante la
 * inicialización, cuando la BD puede no estar disponible). Si existe, se recuerda para siempre. Si no, se
 * vuelve a preguntar cada {@link #REINTENTO}: así, al aplicar la migración, el autor aparece en minutos
 * también en las Lambdas que ya estaban calientes, sin esperar a un arranque en frío o a un redespliegue.
 */
@Singleton
public class ColumnaAutorAnalisis {

    /** Cada cuánto se vuelve a buscar la columna mientras no exista. */
    static final Duration REINTENTO = Duration.ofMinutes(5);

    private static final Logger LOG = LoggerFactory.getLogger(ColumnaAutorAnalisis.class);

    // Esquemas del search_path: el mismo en el que el resto del SQL resuelve "analisis".
    private static final String SQL = """
            SELECT count(*) FROM information_schema.columns
            WHERE table_schema = ANY (current_schemas(false)) AND table_name = 'analisis' AND column_name = 'id_cuenta'
            """;

    private final JdbcOperations jdbc;
    private final Clock reloj;
    private volatile boolean existe;
    private volatile Instant siguienteConsulta = Instant.MIN;

    public ColumnaAutorAnalisis(JdbcOperations jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    ColumnaAutorAnalisis(JdbcOperations jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    public boolean disponible() {
        if (existe) {
            return true;
        }
        Instant ahora = reloj.instant();
        if (ahora.isBefore(siguienteConsulta)) {
            return false;
        }
        synchronized (this) {
            if (existe || ahora.isBefore(siguienteConsulta)) {
                return existe;
            }
            boolean hay = consultar();
            if (hay) {
                if (siguienteConsulta != Instant.MIN) {
                    LOG.info("analisis.id_cuenta ya existe: desde ahora se responde el autor de cada análisis");
                }
                existe = true;
            } else {
                if (siguienteConsulta == Instant.MIN) {
                    LOG.warn("analisis.id_cuenta no existe: se responde autor = null y seguimiento vacío (se vuelve a "
                            + "revisar cada {} min). Aplicar en la BD de TT_BKN "
                            + "docker/postgres/migraciones/2026-11-17_analisis_id_cuenta.sql", REINTENTO.toMinutes());
                }
                siguienteConsulta = ahora.plus(REINTENTO);
            }
            return hay;
        }
    }

    /** Protegido para las pruebas que simulan una BD sin la columna. */
    protected boolean consultar() {
        return Sql.lista(jdbc, SQL, List.of(), rs -> rs.getLong(1)).getFirst() > 0;
    }
}
