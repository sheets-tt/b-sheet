package tt.sheets.analisis.infrastructure;

import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import tt.sheets.analisis.domain.FirmadorEvidencias;

import java.net.URI;
import java.time.Duration;

/** Adaptador: presigned GET de S3 con las credenciales del rol de la Lambda. */
class S3FirmadorEvidencias implements FirmadorEvidencias {

    private final S3Presigner presigner;
    private final String bucket;

    S3FirmadorEvidencias(S3Presigner presigner, String bucket) {
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public URI firmar(String clave, Duration vigencia) {
        GetObjectPresignRequest pedido = GetObjectPresignRequest.builder()
                .signatureDuration(vigencia)
                .getObjectRequest(get -> get.bucket(bucket).key(clave))
                .build();
        return URI.create(presigner.presignGetObject(pedido).url().toString());
    }
}
