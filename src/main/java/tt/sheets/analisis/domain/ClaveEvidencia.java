package tt.sheets.analisis.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reglas sobre ruta_evidencia (la escribe TT_BKN): qué foto se puede servir y cuál no.
 * TT_BKN guarda la URL completa de S3 (https://bucket.s3.region.amazonaws.com/evidencias/x.jpg),
 * a veces solo la clave, o "sin_imagen". El bucket también guarda despliegues: solo se acepta
 * una clave de ESTE bucket, bajo el prefijo de evidencias, sin segmentos "." ni "..".
 */
public final class ClaveEvidencia {

    private static final String SIN_IMAGEN = "sin_imagen";

    private ClaveEvidencia() {
    }

    public static boolean tieneEvidencia(String ruta) {
        return ruta != null && !ruta.isBlank() && !SIN_IMAGEN.equals(ruta.trim());
    }

    /** Clave de S3 dentro de {@code prefijo} en {@code bucket}, o vacío si no hay foto o la ruta no es válida. */
    public static Optional<String> extraer(String ruta, String bucket, String prefijo) {
        if (!tieneEvidencia(ruta)) {
            return Optional.empty();
        }
        String texto = ruta.trim();
        Optional<String> clave = texto.contains("://") ? claveDeUrl(texto, bucket) : Optional.of(texto);
        return clave.filter(c -> permitida(c, prefijo));
    }

    private static Optional<String> claveDeUrl(String texto, String bucket) {
        URI uri;
        try {
            uri = new URI(texto);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        boolean limpia = "https".equals(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1
                && uri.getRawQuery() == null && uri.getRawFragment() == null;
        String host = uri.getHost();
        // Estilo virtual-hosted, global o regional: bucket.s3.amazonaws.com | bucket.s3.<region>.amazonaws.com
        boolean deEsteBucket = host != null && (host.equals(bucket + ".s3.amazonaws.com")
                || Pattern.matches(Pattern.quote(bucket) + "\\.s3\\.[a-z0-9-]+\\.amazonaws\\.com", host));
        String path = uri.getPath(); // ya decodificado: %2E%2E llega como ".."
        if (!limpia || !deEsteBucket || path == null || !path.startsWith("/")) {
            return Optional.empty();
        }
        return Optional.of(path.substring(1));
    }

    private static boolean permitida(String clave, String prefijo) {
        if (!clave.startsWith(prefijo) || clave.length() == prefijo.length()) {
            return false;
        }
        for (String segmento : clave.split("/", -1)) {
            if (segmento.isEmpty() || segmento.equals(".") || segmento.equals("..")) {
                return false;
            }
        }
        return true;
    }
}
