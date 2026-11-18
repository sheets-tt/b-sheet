package tt.sheets.analisis.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import jakarta.inject.Singleton;
import tt.sheets.analisis.domain.Analisis;
import tt.sheets.analisis.domain.AnalisisRepository;
import tt.sheets.analisis.domain.Cromatico;
import tt.sheets.analisis.domain.EstadoSincronizacion;
import tt.sheets.analisis.domain.FiltroAnalisis;
import tt.sheets.analisis.domain.Resultado;
import tt.sheets.analisis.domain.TipoImagen;
import tt.sheets.compartido.infrastructure.ColumnaAutorAnalisis;
import tt.sheets.compartido.infrastructure.Sql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador SQL: filtros opcionales y JOIN con analisis_cromatico, que no se expresan bien con
 * repositorios derivados. Solo SELECT, siempre con parámetros enlazados (nunca concatenando valores).
 */
@Singleton
class JdbcAnalisisRepository implements AnalisisRepository {

    // El esquema no impide varias filas de cromático por análisis: se toma una de forma determinista.
    // %s: columnas del autor (con o sin analisis.id_cuenta, ver ColumnaAutorAnalisis).
    private static final String SELECT = """
            SELECT a.id_analisis, a.ruta_evidencia, a.latitud_captura, a.longitud_captura, a.fecha_captura,
                   a.fecha_sincronizacion, a.estado_sincronizacion, a.tipo_imagen, a.resultado_clasificacion,
                   a.confianza, a.observaciones, a.id_arbol, a.id_zona,
                   c.pct_sano, c.pct_regular, c.pct_danado, c.pct_incierto,
                   %s
            FROM analisis a
            LEFT JOIN LATERAL (
                SELECT pct_sano, pct_regular, pct_danado, pct_incierto
                FROM analisis_cromatico
                WHERE id_analisis = a.id_analisis
                ORDER BY id_analisis_cromatico
                LIMIT 1
            ) c ON true
            %s""";

    // Solo nombre_completo: el correo y el hash de la cuenta nunca salen de aquí.
    private static final String SELECT_CON_AUTOR = SELECT.formatted(
            "au.id_cuenta AS autor_id, au.nombre_completo AS autor_nombre",
            "LEFT JOIN cuenta au ON au.id_cuenta = a.id_cuenta\n");

    private static final String SELECT_SIN_AUTOR = SELECT.formatted(
            "NULL::uuid AS autor_id, NULL::varchar AS autor_nombre", "");

    private final JdbcOperations jdbc;

    private final ColumnaAutorAnalisis columnaAutor;

    JdbcAnalisisRepository(JdbcOperations jdbc, ColumnaAutorAnalisis columnaAutor) {
        this.jdbc = jdbc;
        this.columnaAutor = columnaAutor;
    }

    private String select() {
        return columnaAutor.disponible() ? SELECT_CON_AUTOR : SELECT_SIN_AUTOR;
    }

    @Override
    public List<Analisis> buscar(FiltroAnalisis f) {
        List<String> condiciones = new ArrayList<>();
        List<Object> parametros = new ArrayList<>();
        agregar(condiciones, parametros, "a.tipo_imagen = ?", f.tipo() == null ? null : f.tipo().valor());
        agregar(condiciones, parametros, "a.resultado_clasificacion = ?",
                f.resultado() == null ? null : f.resultado().valor());
        agregar(condiciones, parametros, "a.estado_sincronizacion = ?", f.estado() == null ? null : f.estado().valor());
        agregar(condiciones, parametros, "a.fecha_captura >= ?", Sql.utc(f.desde()));
        agregar(condiciones, parametros, "a.fecha_captura <= ?", Sql.utc(f.hasta()));
        agregar(condiciones, parametros, "a.id_arbol = ?", f.idArbol());
        agregar(condiciones, parametros, "a.id_zona = ?", f.idZona());
        parametros.add(f.limite());

        String sql = select()
                + (condiciones.isEmpty() ? "" : "WHERE " + String.join(" AND ", condiciones) + "\n")
                + "ORDER BY a.fecha_captura DESC, a.id_analisis\nLIMIT ?";
        return consultar(sql, parametros);
    }

    @Override
    public Optional<Analisis> findById(UUID id) {
        return consultar(select() + "WHERE a.id_analisis = ?", List.of(id)).stream().findFirst();
    }

    private static void agregar(List<String> condiciones, List<Object> parametros, String condicion, Object valor) {
        if (valor != null) {
            condiciones.add(condicion);
            parametros.add(valor);
        }
    }

    private List<Analisis> consultar(String sql, List<Object> parametros) {
        return Sql.lista(jdbc, sql, parametros, JdbcAnalisisRepository::leer);
    }

    private static Analisis leer(ResultSet rs) throws SQLException {
        Double pctSano = rs.getObject("pct_sano", Double.class);
        Cromatico cromatico = pctSano == null ? null : new Cromatico(pctSano,
                rs.getObject("pct_regular", Double.class), rs.getObject("pct_danado", Double.class),
                rs.getObject("pct_incierto", Double.class));
        return new Analisis(
                rs.getObject("id_analisis", UUID.class),
                rs.getString("ruta_evidencia"),
                rs.getObject("latitud_captura", Double.class),
                rs.getObject("longitud_captura", Double.class),
                Sql.instante(rs, "fecha_captura"),
                Sql.instante(rs, "fecha_sincronizacion"),
                EstadoSincronizacion.desde(rs.getString("estado_sincronizacion")),
                TipoImagen.desde(rs.getString("tipo_imagen")),
                Resultado.desde(rs.getString("resultado_clasificacion")),
                rs.getObject("confianza", Double.class),
                rs.getString("observaciones"),
                rs.getObject("id_arbol", UUID.class),
                rs.getObject("id_zona", UUID.class),
                cromatico,
                Sql.autor(rs, "autor_id", "autor_nombre"));
    }
}
