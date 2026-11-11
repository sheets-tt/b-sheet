package tt.sheets.analisis.domain;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Enum del dominio guardado en TT_BKN como texto (los valores de sus CHECK). */
public interface ValorBd {

    String valor();

    /** Convierte el texto de la BD o de un filtro; el mensaje dice qué valores se aceptan. */
    static <E extends Enum<E> & ValorBd> E desde(Class<E> tipo, String nombre, String valor) {
        for (E e : tipo.getEnumConstants()) {
            if (e.valor().equals(valor)) {
                return e;
            }
        }
        String valores = Arrays.stream(tipo.getEnumConstants()).map(ValorBd::valor).collect(Collectors.joining(", "));
        throw new IllegalArgumentException(nombre + " inválido: " + valor + " (valores: " + valores + ")");
    }
}
