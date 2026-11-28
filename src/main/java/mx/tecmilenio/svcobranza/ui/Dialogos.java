package mx.tecmilenio.svcobranza.ui;

import java.time.LocalDate;
import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Mensajes y confirmaciones con los botones en español y el tema de la aplicacion. */
public final class Dialogos {

    public static final ButtonType GUARDAR = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
    public static final ButtonType CANCELAR = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

    private static final String TITULO = "SV-Cobranza";

    private Dialogos() {
    }

    public static void error(String mensaje) {
        mostrar(Alert.AlertType.ERROR, mensaje, new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE));
    }

    public static void info(String mensaje) {
        mostrar(Alert.AlertType.INFORMATION, mensaje, new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE));
    }

    /** Pregunta con dos botones; devuelve true si se elige el de aceptar. */
    public static boolean confirmar(String mensaje, String textoAceptar) {
        return confirmar(mensaje, textoAceptar, CANCELAR.getText());
    }

    /** Pregunta con dos botones con el texto indicado; devuelve true si se elige el primero. */
    public static boolean confirmar(String mensaje, String textoAceptar, String textoRechazar) {
        ButtonType aceptar = new ButtonType(textoAceptar, ButtonBar.ButtonData.OK_DONE);
        ButtonType rechazar = new ButtonType(textoRechazar, ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alerta = crear(Alert.AlertType.CONFIRMATION, mensaje, aceptar, rechazar);
        Optional<ButtonType> respuesta = alerta.showAndWait();
        return respuesta.isPresent() && respuesta.get() == aceptar;
    }

    /** Pide una fecha (dd/mm/aaaa). Vacio si se cancela o se deja en blanco; AppException si esta mal escrita. */
    public static Optional<LocalDate> fecha(String mensaje, LocalDate inicial) {
        DatePicker selector = Fechas.selector(inicial);
        Label texto = new Label(mensaje);
        texto.setWrapText(true);
        texto.setMaxWidth(380);
        Alert alerta = crear(Alert.AlertType.NONE, "", GUARDAR, CANCELAR);
        alerta.getDialogPane().setContent(new VBox(10, texto, selector));
        Optional<ButtonType> respuesta = alerta.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != GUARDAR) {
            return Optional.empty();
        }
        return Optional.ofNullable(Entradas.fecha(Fechas.texto(selector), "la fecha"));
    }

    /** Pide un texto libre (puede quedar en blanco). Vacio solo si se cancela. */
    public static Optional<String> texto(String mensaje, String textoAceptar) {
        TextField campo = new TextField();
        Label etiqueta = new Label(mensaje);
        etiqueta.setWrapText(true);
        etiqueta.setMaxWidth(380);
        ButtonType aceptar = new ButtonType(textoAceptar, ButtonBar.ButtonData.OK_DONE);
        Alert alerta = crear(Alert.AlertType.NONE, "", aceptar, CANCELAR);
        alerta.getDialogPane().setContent(new VBox(10, etiqueta, campo));
        Optional<ButtonType> respuesta = alerta.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != aceptar) {
            return Optional.empty();
        }
        return Optional.of(campo.getText() == null ? "" : campo.getText());
    }

    /** Crea un cuadro con el tema aplicado; lo usan las ventanas propias (pago, aviso del dia). */
    public static Alert crear(Alert.AlertType tipo, String mensaje, ButtonType... botones) {
        Alert alerta = new Alert(tipo, mensaje, botones);
        alerta.setTitle(TITULO);
        alerta.setHeaderText(null);
        alerta.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        Tema.aplicar(alerta.getDialogPane());
        return alerta;
    }

    private static void mostrar(Alert.AlertType tipo, String mensaje, ButtonType boton) {
        crear(tipo, mensaje, boton).showAndWait();
    }
}
