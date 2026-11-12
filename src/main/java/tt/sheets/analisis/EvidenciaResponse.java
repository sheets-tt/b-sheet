package tt.sheets.analisis;

import io.micronaut.serde.annotation.Serdeable;

/** {"url": URL firmada de S3, "expira": instante ISO-8601 en que deja de funcionar}. */
@Serdeable
public record EvidenciaResponse(String url, String expira) {
}
