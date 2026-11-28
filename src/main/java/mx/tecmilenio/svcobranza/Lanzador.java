package mx.tecmilenio.svcobranza;

import javafx.application.Application;

/**
 * Punto de entrada del jar. No extiende {@link Application}: con una clase principal que si la
 * extiende, Java exige JavaFX en el module path y el jar con todo incluido no arrancaria.
 */
public final class Lanzador {

    private Lanzador() {
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
