package tt.sheets.analisis.domain;

import java.net.URI;
import java.time.Duration;

/** Puerto de salida: genera una URL temporal para leer una foto privada (S3 presigned GET). */
public interface FirmadorEvidencias {

    URI firmar(String clave, Duration vigencia);
}
