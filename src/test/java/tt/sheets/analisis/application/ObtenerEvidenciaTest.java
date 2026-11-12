package tt.sheets.analisis.application;

import org.junit.jupiter.api.Test;
import tt.sheets.analisis.domain.Analisis;
import tt.sheets.analisis.domain.AnalisisRepository;
import tt.sheets.analisis.domain.EstadoSincronizacion;
import tt.sheets.analisis.domain.FiltroAnalisis;
import tt.sheets.analisis.domain.FirmadorEvidencias;
import tt.sheets.analisis.domain.Resultado;
import tt.sheets.analisis.domain.TipoImagen;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Firma la foto de un análisis solo si existe, tiene foto y su ruta es una clave permitida. */
class ObtenerEvidenciaTest {

    private static final Instant AHORA = Instant.parse("2026-11-09T18:00:00Z");
    private static final Duration VIGENCIA = Duration.ofMinutes(5);

    private final List<String> firmadas = new ArrayList<>();
    private final FirmadorEvidencias firmador = (clave, vigencia) -> {
        firmadas.add(clave + "@" + vigencia);
        return URI.create("https://firmada.example/" + clave);
    };

    private static Analisis conRuta(UUID id, String ruta) {
        return new Analisis(id, ruta, null, null, AHORA, null, EstadoSincronizacion.SINCRONIZADO,
                TipoImagen.CORTEZA, Resultado.SANO, null, null, UUID.randomUUID(), null, null);
    }

    private ObtenerEvidencia caso(Analisis... existentes) {
        AnalisisRepository repo = new AnalisisRepository() {
            public List<Analisis> buscar(FiltroAnalisis filtro) { return List.of(existentes); }
            public Optional<Analisis> findById(UUID id) {
                return List.of(existentes).stream().filter(a -> a.id().equals(id)).findFirst();
            }
        };
        return new ObtenerEvidencia(repo, firmador, "descortezadores-archivos-prod", "evidencias/", VIGENCIA,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void signsTheKeyAndReportsExpiry() {
        UUID id = UUID.randomUUID();
        Optional<EvidenciaFirmada> e = caso(conRuta(id,
                "https://descortezadores-archivos-prod.s3.us-east-1.amazonaws.com/evidencias/x.jpg")).obtener(id);

        assertEquals(Optional.of(new EvidenciaFirmada(URI.create("https://firmada.example/evidencias/x.jpg"),
                AHORA.plus(VIGENCIA))), e);
        assertEquals(List.of("evidencias/x.jpg@PT5M"), firmadas);
    }

    @Test
    void unknownAnalysisSignsNothing() {
        assertEquals(Optional.empty(), caso().obtener(UUID.randomUUID()));
        assertEquals(List.of(), firmadas);
    }

    @Test
    void analysisWithoutImageOrForbiddenKeySignsNothing() {
        UUID sinFoto = UUID.randomUUID();
        UUID prohibida = UUID.randomUUID();
        ObtenerEvidencia obtener = caso(conRuta(sinFoto, "sin_imagen"), conRuta(prohibida, "deployments/app.jar"));
        assertEquals(Optional.empty(), obtener.obtener(sinFoto));
        assertEquals(Optional.empty(), obtener.obtener(prohibida));
        assertEquals(List.of(), firmadas);
    }
}
