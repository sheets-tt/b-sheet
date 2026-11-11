package tt.sheets.analisis.infrastructure;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La firma SigV4 se calcula localmente: no hace falta red ni credenciales reales. */
class S3FirmadorEvidenciasTest {

    @Test
    void presignsAGetOnTheEvidenceBucket() {
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATEST", "secret")))
                .build()) {
            URI url = new S3FirmadorEvidencias(presigner, "descortezadores-archivos-prod")
                    .firmar("evidencias/e1.jpg", Duration.ofMinutes(5));

            assertEquals("https", url.getScheme());
            // En us-east-1 el SDK firma contra el endpoint global (la CSP de f-sheet permite este host).
        assertEquals("descortezadores-archivos-prod.s3.amazonaws.com", url.getHost());
            assertEquals("/evidencias/e1.jpg", url.getPath());
            assertTrue(url.getQuery().contains("X-Amz-Expires=300"), url.toString());
            assertTrue(url.getQuery().contains("X-Amz-Signature="), url.toString());
        }
    }
}
