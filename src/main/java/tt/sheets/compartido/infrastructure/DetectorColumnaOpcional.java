package tt.sheets.compartido.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Consulta en {@code information_schema.columns} si una columna opcional ya existe en {@code public}/search_path.
 * <p>
 * Se consulta en el primer uso (no al construir el bean: en Lambda los singletons se crean durante la
 * inicialización, cuando la BD puede no estar disponible). Si existe, se recuerda para siempre. Si no, se
 * vuelve a preguntar cada {@link #REINTENTO} para detectarla sin redesplegar ni esperar arranque en frío.
 */
public abstract class DetectorColumnaOpcional {

    /** Cada cuánto se vuelve a buscar la columna mientras no exista. */
    public static final Duration REINTENTO = Duration.ofMinutes(5);

    private static final Logger LOG = LoggerFactory.getLogger(DetectorColumnaOpcional.class);

    private static final String SQL = """
            SELECT count(*) FROM information_schema.columns
            WHERE table_schema = ANY (current_schemas(false)) AND table_name = ? AND column_name = ?
            """;

    private final JdbcOperations jdbc;
    private final Clock reloj;
    private final String tabla;
    private final String columna;
    private final String avisoAusente;
    private final String avisoPresente;
    private volatile boolean existe;
    private volatile Instant siguienteConsulta = Instant.MIN;

    protected DetectorColumnaOpcional(
            JdbcOperations jdbc,
            Clock reloj,
            String tabla,
            String columna,
            String avisoAusente,
            String avisoPresente) {
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.tabla = tabla;
        this.columna = columna;
        this.avisoAusente = avisoAusente;
        this.avisoPresente = avisoPresente;
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
                    LOG.info(avisoPresente);
                }
                existe = true;
            } else {
                if (siguienteConsulta == Instant.MIN) {
                    LOG.warn(avisoAusente, REINTENTO.toMinutes());
                }
                siguienteConsulta = ahora.plus(REINTENTO);
            }
            return hay;
        }
    }

    /** Protegido para las pruebas que simulan una BD sin la columna. */
    protected boolean consultar() {
        return Sql.lista(jdbc, SQL, List.of(tabla, columna), rs -> rs.getLong(1)).getFirst() > 0;
    }
}
