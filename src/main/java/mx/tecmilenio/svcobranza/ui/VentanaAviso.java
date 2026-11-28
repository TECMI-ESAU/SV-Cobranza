package mx.tecmilenio.svcobranza.ui;

import java.time.LocalDate;
import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import mx.tecmilenio.svcobranza.model.Recordatorio;

/**
 * Ventana emergente con los recordatorios de cobro: se abre sola una vez al dia al iniciar la
 * aplicacion y se puede volver a abrir con el boton de la pestaña Cobros pendientes.
 */
public final class VentanaAviso {

    private VentanaAviso() {
    }

    /** Al abrir la aplicacion: solo muestra algo si hay recordatorios y hoy aun no se mostraron. */
    public static void alAbrir(Contexto ctx) {
        List<Recordatorio> lista = ctx.app().recordatorios().avisoDelDia();
        if (!lista.isEmpty()) {
            mostrar(lista);
        }
    }

    /** Consulta manual: muestra los recordatorios vigentes hoy, aunque ya se hayan mostrado. */
    public static void consultar(Contexto ctx) {
        List<Recordatorio> lista = ctx.app().recordatorios().recordatorios();
        if (lista.isEmpty()) {
            Dialogos.info("No hay cobros que avisar por hoy.");
        } else {
            mostrar(lista);
        }
    }

    private static void mostrar(List<Recordatorio> lista) {
        LocalDate hoy = LocalDate.now();
        Label encabezado = new Label("Cobros por atender (" + Formatos.fecha(hoy) + ")");
        encabezado.getStyleClass().add("titulo-seccion");
        Label nota = new Label("Puedes consultar este aviso de nuevo con el botón de la pestaña Cobros pendientes.");
        nota.setWrapText(true);
        nota.getStyleClass().add("nota");

        VBox filas = new VBox(6);
        for (Recordatorio r : lista) {
            Label fila = new Label(r.cobro().usuario() + " — " + r.cobro().categoria() + " "
                    + Formatos.moneda(r.cobro().monto()) + " — " + Avisos.plazo(r.dias()) + " ("
                    + Formatos.fecha(r.cobro().fechaLimite()) + ")");
            fila.setWrapText(true);
            fila.setMaxWidth(Double.MAX_VALUE);
            fila.getStyleClass().addAll("panel-aviso", Avisos.estilo(r.color()));
            filas.getChildren().add(fila);
        }
        ScrollPane desplazable = new ScrollPane(filas);
        desplazable.setFitToWidth(true);
        desplazable.setPrefViewportHeight(Math.min(360, 46.0 * lista.size() + 8));
        desplazable.setPrefViewportWidth(560);

        VBox contenido = new VBox(10, encabezado, desplazable, nota);
        contenido.setPadding(new Insets(4));
        ButtonType entendido = new ButtonType("Entendido", ButtonBar.ButtonData.OK_DONE);
        Alert alerta = Dialogos.crear(Alert.AlertType.NONE, "", entendido);
        alerta.getDialogPane().setContent(contenido);
        alerta.showAndWait();
    }
}
