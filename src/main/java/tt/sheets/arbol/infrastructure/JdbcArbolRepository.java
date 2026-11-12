package tt.sheets.arbol.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import jakarta.inject.Singleton;
import tt.sheets.arbol.domain.Arbol;
import tt.sheets.arbol.domain.ArbolRepository;
import tt.sheets.compartido.infrastructure.Sql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador SQL: tabla arbol + su análisis más reciente (para el mapa, sin traer el historial). */
@Singleton
class JdbcArbolRepository implements ArbolRepository {

    private static final String SELECT = """
            SELECT a.id_arbol, a.nombre_especifico, a.especie, a.latitud, a.longitud, a.fecha_registro,
                   a.descripcion, u.ultimo_id, u.ultimo_resultado, u.ultimo_fecha
            FROM arbol a
            """ + Sql.joinUltimoAnalisis("id_arbol", "a.id_arbol");

    private final JdbcOperations jdbc;

    JdbcArbolRepository(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Arbol> findAll() {
        return Sql.lista(jdbc, SELECT + "ORDER BY a.fecha_registro DESC NULLS LAST, a.id_arbol", List.of(),
                JdbcArbolRepository::leer);
    }

    @Override
    public Optional<Arbol> findById(UUID id) {
        return Sql.lista(jdbc, SELECT + "WHERE a.id_arbol = ?", List.of(id), JdbcArbolRepository::leer)
                .stream().findFirst();
    }

    private static Arbol leer(ResultSet rs) throws SQLException {
        return new Arbol(
                rs.getObject("id_arbol", UUID.class),
                rs.getString("nombre_especifico"),
                rs.getString("especie"),
                rs.getObject("latitud", Double.class),
                rs.getObject("longitud", Double.class),
                Sql.instante(rs, "fecha_registro"),
                rs.getString("descripcion"),
                Sql.ultimoAnalisis(rs));
    }
}
