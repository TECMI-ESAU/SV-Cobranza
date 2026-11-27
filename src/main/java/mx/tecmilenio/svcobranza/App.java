package mx.tecmilenio.svcobranza;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Punto de entrada. Los datos se guardan en la carpeta del usuario: ~/SV-Cobranza-datos.
 * La interfaz JavaFX llega en la rama feature/v2-javafx-base.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        Path carpeta = Paths.get(System.getProperty("user.home"), "SV-Cobranza-datos");
        try (Aplicacion app = Aplicacion.enCarpeta(carpeta)) {
            app.experiencia().completar();
            app.cobros().generarTodos();
            System.out.println("SV-Cobranza: base de datos lista en " + carpeta);
        } catch (AppException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
