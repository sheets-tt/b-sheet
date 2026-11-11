package tt.sheets.zona.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import jakarta.inject.Singleton;
import tt.sheets.compartido.infrastructure.Sql;
import tt.sheets.zona.domain.ZonaFollaje;
import tt.sheets.zona.domain.ZonaFollajeRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador SQL: tabla zona_follaje + su análisis más reciente. */
@Singleton
class JdbcZonaFollajeRepository implements ZonaFollajeRepository {

    private static final String SELECT = """
            SELECT z.id_zona, z.nombre_zona, z.latitud_centro, z.longitud_centro, z.radio_metros,
                   z.fecha_registro, z.descripcion, u.ultimo_id, u.ultimo_resultado, u.ultimo_fecha
            FROM zona_follaje z
            """ + Sql.joinUltimoAnalisis("id_zona", "z.id_zona");

    private final JdbcOperations jdbc;

    JdbcZonaFollajeRepository(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ZonaFollaje> findAll() {
        return Sql.lista(jdbc, SELECT + "ORDER BY z.fecha_registro DESC NULLS LAST, z.id_zona", List.of(),
                JdbcZonaFollajeRepository::leer);
    }

    @Override
    public Optional<ZonaFollaje> findById(UUID id) {
        return Sql.lista(jdbc, SELECT + "WHERE z.id_zona = ?", List.of(id), JdbcZonaFollajeRepository::leer)
                .stream().findFirst();
    }

    private static ZonaFollaje leer(ResultSet rs) throws SQLException {
        return new ZonaFollaje(
                rs.getObject("id_zona", UUID.class),
                rs.getString("nombre_zona"),
                rs.getObject("latitud_centro", Double.class),
                rs.getObject("longitud_centro", Double.class),
                rs.getObject("radio_metros", Double.class),
                Sql.instante(rs, "fecha_registro"),
                rs.getString("descripcion"),
                Sql.ultimoAnalisis(rs));
    }
}
