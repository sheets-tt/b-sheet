package tt.sheets.compartido.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import tt.sheets.analisis.domain.Resultado;
import tt.sheets.analisis.domain.UltimoAnalisis;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lo común de los adaptadores SQL de solo lectura: conexión administrada por Micronaut,
 * parámetros siempre enlazados y conversión de timestamptz.
 */
public final class Sql {

    private Sql() {
    }

    @FunctionalInterface
    public interface Fila<T> {
        T leer(ResultSet rs) throws SQLException;
    }

    public static <T> List<T> lista(JdbcOperations jdbc, String sql, List<?> parametros, Fila<T> fila) {
        return jdbc.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parametros.size(); i++) {
                    statement.setObject(i + 1, parametros.get(i));
                }
                try (ResultSet rs = statement.executeQuery()) {
                    List<T> filas = new ArrayList<>();
                    while (rs.next()) {
                        filas.add(fila.leer(rs));
                    }
                    return filas;
                }
            }
        });
    }

    /** Consultas de agregados que siempre devuelven exactamente una fila. */
    public static <T> T una(JdbcOperations jdbc, String sql, Fila<T> fila) {
        return lista(jdbc, sql, List.of(), fila).getFirst();
    }

    public static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    public static OffsetDateTime utc(Instant instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }

    /**
     * JOIN con el análisis más reciente de cada fila; agrega las columnas ultimo_id, ultimo_resultado y
     * ultimo_fecha. {@code columnaAnalisis} y {@code columnaPropia} son constantes del adaptador, nunca
     * entrada del usuario.
     */
    public static String joinUltimoAnalisis(String columnaAnalisis, String columnaPropia) {
        return """
                LEFT JOIN LATERAL (
                    SELECT id_analisis AS ultimo_id, resultado_clasificacion AS ultimo_resultado,
                           fecha_captura AS ultimo_fecha
                    FROM analisis
                    WHERE %s = %s
                    ORDER BY fecha_captura DESC, id_analisis
                    LIMIT 1
                ) u ON true
                """.formatted(columnaAnalisis, columnaPropia);
    }

    public static UltimoAnalisis ultimoAnalisis(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("ultimo_id", UUID.class);
        return id == null ? null
                : new UltimoAnalisis(id, Resultado.desde(rs.getString("ultimo_resultado")), instante(rs, "ultimo_fecha"));
    }
}
