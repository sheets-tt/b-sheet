package tt.sheets.analisis.application;

import tt.sheets.analisis.domain.Analisis;
import tt.sheets.analisis.domain.AnalisisRepository;
import tt.sheets.analisis.domain.ClaveEvidencia;
import tt.sheets.analisis.domain.FirmadorEvidencias;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Caso de uso: URL temporal de la foto de un análisis. */
public class ObtenerEvidencia {

    private final AnalisisRepository analisis;
    private final FirmadorEvidencias firmador;
    private final String bucket;
    private final String prefijo;
    private final Duration vigencia;
    private final Clock reloj;

    public ObtenerEvidencia(AnalisisRepository analisis, FirmadorEvidencias firmador, String bucket, String prefijo,
                            Duration vigencia, Clock reloj) {
        this.analisis = analisis;
        this.firmador = firmador;
        this.bucket = bucket;
        this.prefijo = prefijo;
        this.vigencia = vigencia;
        this.reloj = reloj;
    }

    /** Vacío si el análisis no existe, no tiene foto o su ruta no es una clave de evidencia permitida. */
    public Optional<EvidenciaFirmada> obtener(UUID idAnalisis) {
        return analisis.findById(idAnalisis)
                .flatMap(a -> clave(a))
                .map(clave -> new EvidenciaFirmada(firmador.firmar(clave, vigencia), reloj.instant().plus(vigencia)));
    }

    /** Si vale la pena ofrecer "Ver foto" para este análisis (misma regla que {@link #obtener}). */
    public boolean disponible(Analisis a) {
        return clave(a).isPresent();
    }

    private Optional<String> clave(Analisis a) {
        return ClaveEvidencia.extraer(a.rutaEvidencia(), bucket, prefijo);
    }
}
