package tt.sheets.estadistica;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import tt.sheets.analisis.AnalisisResponse.CromaticoResponse;
import tt.sheets.analisis.domain.ValorBd;
import tt.sheets.estadistica.domain.Estadisticas;
import tt.sheets.estadistica.domain.Totales;

import java.util.LinkedHashMap;
import java.util.Map;

/** JSON de GET /estadisticas. Las claves de los mapas son los valores de la BD, en orden fijo. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Serdeable
public record EstadisticasResponse(TotalesResponse totales, Map<String, Long> analisisPorResultado,
                                   Map<String, Long> analisisPorTipo, Map<String, Long> analisisPorSincronizacion,
                                   CromaticoResponse cromaticoPromedio, String ultimaCaptura) {

    @Serdeable
    public record TotalesResponse(long arboles, long zonas, long analisis, long dispositivos,
                                  long arbolesConInfestacion, long zonasConInfestacion) {

        static TotalesResponse from(Totales t) {
            return new TotalesResponse(t.arboles(), t.zonas(), t.analisis(), t.dispositivos(),
                    t.arbolesConInfestacion(), t.zonasConInfestacion());
        }
    }

    static EstadisticasResponse from(Estadisticas e) {
        return new EstadisticasResponse(TotalesResponse.from(e.totales()), porValor(e.porResultado()),
                porValor(e.porTipo()), porValor(e.porSincronizacion()),
                CromaticoResponse.from(e.cromaticoPromedio()),
                e.ultimaCaptura() == null ? null : e.ultimaCaptura().toString());
    }

    private static Map<String, Long> porValor(Map<? extends ValorBd, Long> conteos) {
        Map<String, Long> json = new LinkedHashMap<>();
        conteos.forEach((k, v) -> json.put(k.valor(), v));
        return json;
    }
}
