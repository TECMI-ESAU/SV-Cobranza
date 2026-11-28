package mx.tecmilenio.svcobranza.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import mx.tecmilenio.svcobranza.model.EntradaBitacora;

/** Pestaña Bitácora: registro de altas, ediciones, cancelaciones, pagos, condonaciones y respaldos. */
public final class PestanaBitacora {

    private final Contexto ctx;
    private final VBox raiz;
    private final TableView<EntradaBitacora> tabla = new TableView<>();

    public PestanaBitacora(Contexto ctx) {
        this.ctx = ctx;
        Label titulo = new Label("Bitácora de operaciones");
        titulo.getStyleClass().add("titulo-seccion");
        tabla.setPlaceholder(new Label("Aún no hay operaciones registradas."));
        tabla.getColumns().add(columna("Fecha y hora", 150, b -> Formatos.fechaHora(b.fechaHora())));
        tabla.getColumns().add(columna("Tipo", 100, EntradaBitacora::tipo));
        tabla.getColumns().add(columna("Acción", 120, EntradaBitacora::accion));
        tabla.getColumns().add(columna("Monto", 110, b -> Formatos.moneda(b.monto())));
        tabla.getColumns().add(columna("Detalle", 520, EntradaBitacora::detalle));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        Button actualizar = new Button("Actualizar");
        actualizar.setOnAction(e -> refrescar());

        raiz = new VBox(10, titulo, tabla, actualizar);
        raiz.setPadding(new Insets(16));
        ctx.alCambiar(this::refrescar);
        refrescar();
    }

    public Node nodo() {
        return raiz;
    }

    private void refrescar() {
        tabla.setItems(FXCollections.observableArrayList(ctx.app().bitacora().consultar()));
    }

    private static TableColumn<EntradaBitacora, String> columna(String titulo, double ancho,
            java.util.function.Function<EntradaBitacora, String> valor) {
        TableColumn<EntradaBitacora, String> columna = new TableColumn<>(titulo);
        columna.setPrefWidth(ancho);
        columna.setCellValueFactory(c -> new SimpleStringProperty(valor.apply(c.getValue())));
        return columna;
    }
}
