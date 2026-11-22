package tt.sheets.compartido.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Consulta una sola vez en {@code information_schema.columns} si una columna opcional ya existe
 * en {@code public}/search_path, evitando fallos cuando b-sheet arranca antes que una migración de TT_BKN.
 */
public abstract class DetectorColumnaOpcional {

    private static final Logger LOG = LoggerFactory.getLogger(DetectorColumnaOpcional.class);

    private static final String SQL = """
            SELECT count(*) FROM information_schema.columns
            WHERE table_schema = ANY (current_schemas(false)) AND table_name = ? AND column_name = ?
            """;

    private final JdbcOperations jdbc;
    private final String tabla;
    private final String columna;
    private final String avisoAusente;
    private volatile Boolean disponible;

    protected DetectorColumnaOpcional(JdbcOperations jdbc, String tabla, String columna, String avisoAusente) {
        this.jdbc = jdbc;
        this.tabla = tabla;
        this.columna = columna;
        this.avisoAusente = avisoAusente;
    }

    public boolean disponible() {
        Boolean valor = disponible;
        if (valor == null) {
            synchronized (this) {
                valor = disponible;
                if (valor == null) {
                    valor = Sql.lista(jdbc, SQL, List.of(tabla, columna), rs -> rs.getLong(1)).getFirst() > 0;
                    if (!valor) {
                        LOG.warn(avisoAusente);
                    }
                    disponible = valor;
                }
            }
        }
        return valor;
    }
}
