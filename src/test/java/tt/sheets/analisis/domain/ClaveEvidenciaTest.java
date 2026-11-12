package tt.sheets.analisis.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ruta_evidencia la escribe TT_BKN como URL completa de S3, como clave relativa o como "sin_imagen".
 * Solo se firman claves de este bucket bajo el prefijo de evidencias: el bucket también guarda
 * despliegues (deployments/...) que nunca deben salir por aquí.
 */
class ClaveEvidenciaTest {

    private static final String BUCKET = "descortezadores-archivos-prod";
    private static final String PREFIJO = "evidencias/";

    private static Optional<String> clave(String ruta) {
        return ClaveEvidencia.extraer(ruta, BUCKET, PREFIJO);
    }

    @Test
    void relativeKeyIsUsedAsIs() {
        assertEquals(Optional.of("evidencias/e1.jpg"), clave("evidencias/e1.jpg"));
    }

    @Test
    void regionalS3UrlFromTtBkn() {
        assertEquals(Optional.of("evidencias/0f4fa805-e384-445a-833f-f33a9a8a04c4.jpg"),
                clave("https://descortezadores-archivos-prod.s3.us-east-1.amazonaws.com/evidencias/0f4fa805-e384-445a-833f-f33a9a8a04c4.jpg"));
    }

    @Test
    void globalS3UrlAndPercentEncoding() {
        assertEquals(Optional.of("evidencias/foto 1.jpg"),
                clave("https://descortezadores-archivos-prod.s3.amazonaws.com/evidencias/foto%201.jpg"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "sin_imagen"})
    void noImage(String ruta) {
        assertEquals(Optional.empty(), clave(ruta));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "deployments/backend-0.1-all.jar",
            "https://descortezadores-archivos-prod.s3.us-east-1.amazonaws.com/deployments/backend-0.1-all.jar",
            "evidencias/../deployments/backend-0.1-all.jar",
            "https://descortezadores-archivos-prod.s3.amazonaws.com/evidencias/%2E%2E/deployments/x.jar",
            "evidencias/",
            "https://otro-bucket.s3.us-east-1.amazonaws.com/evidencias/e1.jpg",
            "https://descortezadores-archivos-prod.s3.us-east-1.amazonaws.com.evil.com/evidencias/e1.jpg",
            "http://descortezadores-archivos-prod.s3.us-east-1.amazonaws.com/evidencias/e1.jpg",
            "s3://descortezadores-archivos-prod/evidencias/e1.jpg",
            "https://cdn.example.com/evidencias/e1.jpg",
            "https://descortezadores-archivos-prod.s3.us-east-1.amazonaws.com/evidencias/e1.jpg?x=1",
    })
    void rejectsAnythingOutsideTheEvidenceFolderOfThisBucket(String ruta) {
        assertEquals(Optional.empty(), clave(ruta), ruta);
    }

    @Test
    void tieneEvidenciaOnlyChecksForAnActualPath() {
        assertEquals(true, ClaveEvidencia.tieneEvidencia("evidencias/e1.jpg"));
        assertEquals(false, ClaveEvidencia.tieneEvidencia("sin_imagen"));
        assertEquals(false, ClaveEvidencia.tieneEvidencia(" "));
        assertEquals(false, ClaveEvidencia.tieneEvidencia(null));
    }
}
