package tt.sheets.analisis.infrastructure;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import tt.sheets.analisis.application.ConsultarAnalisis;
import tt.sheets.analisis.application.ObtenerEvidencia;
import tt.sheets.analisis.domain.AnalisisRepository;
import tt.sheets.analisis.domain.FirmadorEvidencias;

import java.time.Clock;
import java.time.Duration;

/** Cablea los casos de uso (que no dependen de Micronaut) con sus adaptadores. */
@Factory
class AnalisisBeans {

    @Singleton
    ConsultarAnalisis consultarAnalisis(AnalisisRepository analisis) {
        return new ConsultarAnalisis(analisis);
    }

    /**
     * Firma local SigV4 (sin red). En Lambda las credenciales son las del rol de la función, que
     * necesita s3:GetObject sobre {bucket}/evidencias/* (ver template.yaml).
     */
    @Singleton
    @Bean(preDestroy = "close")
    S3Presigner s3Presigner(@Value("${evidencias.region}") String region) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    @Singleton
    FirmadorEvidencias firmadorEvidencias(S3Presigner presigner, @Value("${evidencias.bucket}") String bucket) {
        return new S3FirmadorEvidencias(presigner, bucket);
    }

    @Singleton
    ObtenerEvidencia obtenerEvidencia(AnalisisRepository analisis, FirmadorEvidencias firmador,
                                      @Value("${evidencias.bucket}") String bucket,
                                      @Value("${evidencias.prefijo}") String prefijo,
                                      @Value("${evidencias.vigencia}") Duration vigencia) {
        return new ObtenerEvidencia(analisis, firmador, bucket, prefijo, vigencia, Clock.systemUTC());
    }
}
