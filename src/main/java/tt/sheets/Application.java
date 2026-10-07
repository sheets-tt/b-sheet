package tt.sheets;

import io.micronaut.runtime.Micronaut;

/** Punto de entrada local ({@code ./gradlew run}). En Lambda se usa la clase handler APIGatewayV2HTTPEventFunction. */
public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
