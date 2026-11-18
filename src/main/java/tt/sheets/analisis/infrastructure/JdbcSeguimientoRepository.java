package tt.sheets.analisis.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import jakarta.inject.Singleton;
import tt.sheets.analisis.domain.Seguidor;
import tt.sheets.analisis.domain.SeguimientoRepository;
import tt.sheets.compartido.infrastructure.ColumnaAutorAnalisis;
import tt.sheets.compartido.infrastructure.Sql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Adaptador SQL: autores distintos de los análisis de un árbol o zona. JOIN (no LEFT JOIN) con cuenta: los
 * análisis sin autor quedan fuera. Sin analisis.id_cuenta (BD sin migrar) devuelve lista vacía.
 */
@Singleton
class JdbcSeguimientoRepository implements SeguimientoRepository {

    // %s: columna de analisis (constante de esta clase, nunca entrada del usuario).
    private static final String SQL = """
            SELECT c.id_cuenta, c.nombre_completo, count(*) AS analisis, max(a.fecha_captura) AS ultimo
            FROM analisis a
            JOIN cuenta c ON c.id_cuenta = a.id_cuenta
            WHERE a.%s = ?
            GROUP BY c.id_cuenta, c.nombre_completo
            ORDER BY ultimo DESC, c.id_cuenta
            """;

    private final JdbcOperations jdbc;
    private final ColumnaAutorAnalisis columnaAutor;

    JdbcSeguimientoRepository(JdbcOperations jdbc, ColumnaAutorAnalisis columnaAutor) {
        this.jdbc = jdbc;
        this.columnaAutor = columnaAutor;
    }

    @Override
    public List<Seguidor> deArbol(UUID idArbol) {
        return consultar("id_arbol", idArbol);
    }

    @Override
    public List<Seguidor> deZona(UUID idZona) {
        return consultar("id_zona", idZona);
    }

    private List<Seguidor> consultar(String columna, UUID id) {
        if (!columnaAutor.disponible()) {
            return List.of();
        }
        return Sql.lista(jdbc, SQL.formatted(columna), List.of(id), JdbcSeguimientoRepository::leer);
    }

    private static Seguidor leer(ResultSet rs) throws SQLException {
        return new Seguidor(rs.getObject("id_cuenta", UUID.class), rs.getString("nombre_completo"),
                rs.getInt("analisis"), Sql.instante(rs, "ultimo"));
    }
}
