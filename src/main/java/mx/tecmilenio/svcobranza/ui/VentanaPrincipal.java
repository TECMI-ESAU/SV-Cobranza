package mx.tecmilenio.svcobranza.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import mx.tecmilenio.svcobranza.Aplicacion;

/**
 * Ventana principal: encabezado con el logo (si existe) y las ocho pestañas, en el orden
 * acordado con el cliente. Las ocho pestañas estan implementadas; el marcador solo queda por si falta una.
 */
public final class VentanaPrincipal {

    /** Pestañas, en orden. */
    public static final String[] PESTANAS = { "Nuevo Usuario", "Usuarios", "Ingresos", "Egresos",
        "Cobros pendientes", "Balance", "Respaldo", "Bitácora" };

    private final Aplicacion aplicacion;
    private final Contexto contexto;

    public VentanaPrincipal(Aplicacion aplicacion) {
        this.aplicacion = aplicacion;
        this.contexto = new Contexto(aplicacion);
    }

    /** Contexto compartido por las pestañas; App lo usa para abrir el aviso del dia. */
    public Contexto contexto() {
        return contexto;
    }

    public Aplicacion aplicacion() {
        return aplicacion;
    }

    public Parent contenido() {
        BorderPane raiz = new BorderPane();
        raiz.setTop(encabezado());
        raiz.setCenter(pestanas());
        return raiz;
    }

    private Node encabezado() {
        HBox caja = new HBox(12);
        caja.getStyleClass().add("encabezado");
        caja.setAlignment(Pos.CENTER_LEFT);
        caja.setPadding(new Insets(8, 16, 8, 16));
        Tema.logo().ifPresent(imagen -> {
            ImageView vista = new ImageView(imagen);
            vista.setFitHeight(48);
            vista.setPreserveRatio(true);
            vista.setSmooth(true);
            caja.getChildren().add(vista);
        });
        Label titulo = new Label("SV-Cobranza");
        titulo.getStyleClass().add("encabezado-titulo");
        Label subtitulo = new Label("Soplando Vida A.C.");
        subtitulo.getStyleClass().add("encabezado-subtitulo");
        caja.getChildren().addAll(titulo, subtitulo);
        return caja;
    }

    private TabPane pestanas() {
        TabPane panel = new TabPane();
        panel.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (String nombre : PESTANAS) {
            panel.getTabs().add(new Tab(nombre, contenidoDe(nombre)));
        }
        return panel;
    }

    private Node contenidoDe(String nombre) {
        switch (nombre) {
        case "Nuevo Usuario":
            return new PestanaNuevoUsuario(contexto).nodo();
        case "Usuarios":
            return new PestanaUsuarios(contexto).nodo();
        case "Ingresos":
            return new PestanaMovimientos(contexto, mx.tecmilenio.svcobranza.model.TipoMovimiento.INGRESO).nodo();
        case "Egresos":
            return new PestanaMovimientos(contexto, mx.tecmilenio.svcobranza.model.TipoMovimiento.EGRESO).nodo();
        case "Cobros pendientes":
            return new PestanaCobros(contexto).nodo();
        case "Balance":
            return new PestanaBalance(contexto).nodo();
        case "Respaldo":
            return new PestanaRespaldo(contexto).nodo();
        case "Bitácora":
            return new PestanaBitacora(contexto).nodo();
        default:
            return marcador(nombre);
        }
    }

    private static Node marcador(String nombre) {
        Label texto = new Label(nombre);
        texto.getStyleClass().add("marcador");
        return new StackPane(texto);
    }
}
