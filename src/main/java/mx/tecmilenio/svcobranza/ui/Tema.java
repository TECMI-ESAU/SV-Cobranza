package mx.tecmilenio.svcobranza.ui;

import java.io.InputStream;
import java.util.Optional;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

/**
 * Apariencia de la aplicacion: la paleta se toma del logo de Soplando Vida (verde, morado y
 * dorado); el azul y el rojo se reservan para lo funcional (cobros semanales y vencidos).
 */
public final class Tema {

    public static final Color VERDE = Color.web("#4E602A");
    public static final Color MORADO = Color.web("#9060A0");
    public static final Color MORADO_CLARO = Color.web("#C7A6DA");
    public static final Color DORADO = Color.web("#B3904C");
    public static final Color AZUL = Color.web("#2F6DB5");
    public static final Color ROJO = Color.web("#B3261E");

    private static final String HOJA = "/estilos.css";
    private static final String LOGO = "/logo.jpg";

    private Tema() {
    }

    /** Aplica la hoja de estilos de la aplicacion a la escena. */
    public static void aplicar(Scene escena) {
        escena.getStylesheets().add(Tema.class.getResource(HOJA).toExternalForm());
    }

    /** Aplica la hoja de estilos a un cuadro de dialogo (que tiene su propia escena). */
    public static void aplicar(DialogPane dialogo) {
        dialogo.getStylesheets().add(Tema.class.getResource(HOJA).toExternalForm());
    }

    /**
     * Logo de la organizacion. No se publica en el repositorio: si el archivo
     * src/main/resources/logo.jpg no existe, la aplicacion funciona sin el.
     */
    public static Optional<Image> logo() {
        try (InputStream flujo = Tema.class.getResourceAsStream(LOGO)) {
            if (flujo == null) {
                return Optional.empty();
            }
            Image imagen = new Image(flujo);
            return imagen.isError() ? Optional.empty() : Optional.of(imagen);
        } catch (java.io.IOException e) {
            return Optional.empty();
        }
    }

    /** Color en formato web (#rrggbb), para armar estilos en linea. */
    public static String web(Color color) {
        return String.format("#%02X%02X%02X", Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }
}
