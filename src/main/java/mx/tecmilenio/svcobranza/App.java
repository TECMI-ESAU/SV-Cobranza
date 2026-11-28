package mx.tecmilenio.svcobranza;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import mx.tecmilenio.svcobranza.ui.Tema;
import mx.tecmilenio.svcobranza.ui.VentanaAviso;
import mx.tecmilenio.svcobranza.ui.VentanaPrincipal;

/**
 * Aplicacion JavaFX. Los datos se guardan en la carpeta del usuario: ~/SV-Cobranza-datos.
 * Al abrir completa el calendario de experiencia y genera los cobros de los proximos 30 dias.
 */
public class App extends Application {

    private Aplicacion aplicacion;

    @Override
    public void start(Stage escenario) {
        Locale.setDefault(new Locale("es", "MX"));
        try {
            aplicacion = Aplicacion.enCarpeta(carpetaDeDatos());
            aplicacion.experiencia().completar();
            aplicacion.cobros().generarTodos();
        } catch (AppException e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK).showAndWait();
            cerrarAplicacion();
            javafx.application.Platform.exit();
            return;
        }
        VentanaPrincipal ventana = new VentanaPrincipal(aplicacion);
        Scene escena = new Scene(ventana.contenido(), 1100, 720);
        Tema.aplicar(escena);
        escenario.setTitle("SV-Cobranza - Soplando Vida A.C.");
        Tema.logo().ifPresent(imagen -> escenario.getIcons().add(imagen));
        escenario.setScene(escena);
        escenario.setMinWidth(900);
        escenario.setMinHeight(600);
        escenario.show();
        VentanaAviso.alAbrir(ventana.contexto());
    }

    @Override
    public void stop() {
        cerrarAplicacion();
    }

    private void cerrarAplicacion() {
        if (aplicacion != null) {
            aplicacion.close();
            aplicacion = null;
        }
    }

    static Path carpetaDeDatos() {
        return Paths.get(System.getProperty("user.home"), "SV-Cobranza-datos");
    }
}
