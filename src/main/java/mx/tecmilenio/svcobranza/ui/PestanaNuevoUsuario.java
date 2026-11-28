package mx.tecmilenio.svcobranza.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.model.UsuarioAtendido;

/** Pestaña "Nuevo Usuario": el formulario de alta con Guardar y Limpiar. */
public final class PestanaNuevoUsuario {

    private final Contexto ctx;
    private final FormularioUsuario formulario;
    private final ScrollPane raiz;

    public PestanaNuevoUsuario(Contexto ctx) {
        this.ctx = ctx;
        this.formulario = new FormularioUsuario(ctx);

        Label titulo = new Label("Registrar un usuario atendido");
        titulo.getStyleClass().add("titulo-seccion");
        Button guardar = new Button("Guardar usuario");
        guardar.setOnAction(e -> guardar());
        Button limpiar = new Button("Limpiar");
        limpiar.getStyleClass().add("secundario");
        limpiar.setOnAction(e -> formulario.limpiar());

        VBox contenido = new VBox(14, titulo, formulario.nodo(), new HBox(10, guardar, limpiar));
        contenido.setPadding(new Insets(16));
        raiz = new ScrollPane(contenido);
        raiz.setFitToWidth(true);

        // Por si se capturó la primera fecha de experiencia en otra pestaña.
        ctx.alCambiar(formulario::actualizarAviso);
    }

    public Node nodo() {
        return raiz;
    }

    private void guardar() {
        try {
            UsuarioAtendido guardado = ctx.app().usuarios().registrar(formulario.leer());
            formulario.limpiar();
            ctx.cambio();
            Dialogos.info("Se registró a " + guardado.nombre() + ".");
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        }
    }
}
