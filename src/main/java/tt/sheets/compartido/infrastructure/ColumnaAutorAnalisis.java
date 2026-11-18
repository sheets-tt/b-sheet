package tt.sheets.compartido.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ¿Existe analisis.id_cuenta? La agrega la migración 2026-11-17_analisis_id_cuenta.sql de TT_BKN, que se
 * aplica a mano en producción: b-sheet puede desplegarse antes. Sin la columna, los análisis salen con
 * autor = null y el seguimiento vacío, en vez de fallar con "column does not exist".
 * <p>
 * Se consulta una sola vez, en el primer uso (no al construir el bean: en Lambda los singletons se crean
 * durante la inicialización, cuando la BD puede no estar disponible). Si la columna aparece después, se
 * nota al siguiente arranque en frío.
 */
@Singleton
public class ColumnaAutorAnalisis {

    private static final Logger LOG = LoggerFactory.getLogger(ColumnaAutorAnalisis.class);

    // Esquemas del search_path: el mismo en el que el resto del SQL resuelve "analisis".
    private static final String SQL = """
            SELECT count(*) FROM information_schema.columns
            WHERE table_schema = ANY (current_schemas(false)) AND table_name = 'analisis' AND column_name = 'id_cuenta'
            """;

    private final JdbcOperations jdbc;
    private volatile Boolean disponible;

    public ColumnaAutorAnalisis(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    public boolean disponible() {
        Boolean valor = disponible;
        if (valor == null) {
            synchronized (this) {
                valor = disponible;
                if (valor == null) {
                    valor = Sql.lista(jdbc, SQL, List.of(), rs -> rs.getLong(1)).getFirst() > 0;
                    if (!valor) {
                        LOG.warn("analisis.id_cuenta no existe: se responde autor = null y seguimiento vacío. "
                                + "Aplicar en la BD de TT_BKN docker/postgres/migraciones/2026-11-17_analisis_id_cuenta.sql");
                    }
                    disponible = valor;
                }
            }
        }
        return valor;
    }
}
