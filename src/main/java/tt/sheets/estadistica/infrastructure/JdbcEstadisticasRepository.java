package tt.sheets.estadistica.infrastructure;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import jakarta.inject.Singleton;
import tt.sheets.analisis.domain.Cromatico;
import tt.sheets.analisis.domain.EstadoSincronizacion;
import tt.sheets.analisis.domain.Resultado;
import tt.sheets.analisis.domain.TipoImagen;
import tt.sheets.estadistica.domain.EstadisticasRepository;
import tt.sheets.compartido.infrastructure.Sql;
import tt.sheets.estadistica.domain.Totales;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Adaptador SQL de conteos y promedios (solo SELECT, sin parámetros de usuario). */
@Singleton
class JdbcEstadisticasRepository implements EstadisticasRepository {

    private final JdbcOperations jdbc;

    JdbcEstadisticasRepository(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Totales totales() {
        return Sql.una(jdbc, """
                SELECT (SELECT count(*) FROM arbol),
                       (SELECT count(*) FROM zona_follaje),
                       (SELECT count(*) FROM analisis),
                       (SELECT count(*) FROM dispositivo),
                       (SELECT count(DISTINCT id_arbol) FROM analisis WHERE resultado_clasificacion = 'infestado'),
                       (SELECT count(DISTINCT id_zona) FROM analisis WHERE resultado_clasificacion = 'infestado')
                """, rs -> new Totales(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5),
                rs.getLong(6)));
    }

    @Override
    public Map<Resultado, Long> contarPorResultado() {
        return contarPor("resultado_clasificacion", Resultado::desde);
    }

    @Override
    public Map<TipoImagen, Long> contarPorTipo() {
        return contarPor("tipo_imagen", TipoImagen::desde);
    }

    @Override
    public Map<EstadoSincronizacion, Long> contarPorSincronizacion() {
        return contarPor("estado_sincronizacion", EstadoSincronizacion::desde);
    }

    @Override
    public Optional<Cromatico> cromaticoPromedio() {
        // count(*) = 0 -> sin filas de cromático: no hay promedio.
        return Sql.una(jdbc, """
                SELECT count(*),
                       round(avg(pct_sano)::numeric, 2)::float8, round(avg(pct_regular)::numeric, 2)::float8,
                       round(avg(pct_danado)::numeric, 2)::float8, round(avg(pct_incierto)::numeric, 2)::float8
                FROM analisis_cromatico
                """, rs -> rs.getLong(1) == 0 ? Optional.<Cromatico>empty() : Optional.of(new Cromatico(
                rs.getDouble(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5))));
    }

    @Override
    public Optional<Instant> ultimaCaptura() {
        return Sql.una(jdbc, "SELECT max(fecha_captura) FROM analisis",
                rs -> Optional.ofNullable(rs.getObject(1, OffsetDateTime.class)).map(OffsetDateTime::toInstant));
    }

    /** {@code columna} es siempre una constante de esta clase, nunca entrada del usuario. */
    private <E> Map<E, Long> contarPor(String columna, Function<String, E> convertir) {
        Map<E, Long> conteos = new HashMap<>();
        Sql.lista(jdbc, "SELECT " + columna + ", count(*) FROM analisis GROUP BY " + columna, List.of(),
                rs -> conteos.put(convertir.apply(rs.getString(1)), rs.getLong(2)));
        return conteos;
    }
}
