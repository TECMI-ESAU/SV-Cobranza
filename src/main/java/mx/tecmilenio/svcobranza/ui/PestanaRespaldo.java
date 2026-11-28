package mx.tecmilenio.svcobranza.ui;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import mx.tecmilenio.svcobranza.AppException;

/** Pestaña Respaldo: genera un respaldo cifrado con contraseña (formato version 2) y lo restaura. */
public final class PestanaRespaldo {

    private final Contexto ctx;
    private final ScrollPane raiz;

    public PestanaRespaldo(Contexto ctx) {
        this.ctx = ctx;
        Label titulo = new Label("Respaldo y restauración");
        titulo.getStyleClass().add("titulo-seccion");
        Label nota = new Label("El respaldo guarda usuarios, servicios, cobros, ingresos, egresos, el calendario de "
                + "experiencia, los días de aviso y la bitácora, cifrado con una contraseña. Con el archivo y la "
                + "contraseña se puede restaurar en cualquier computadora. La contraseña no se puede recuperar.");
        nota.setWrapText(true);
        nota.getStyleClass().add("nota");
        Button generar = new Button("Generar respaldo...");
        generar.setOnAction(e -> generar());
        Button restaurar = new Button("Restaurar respaldo...");
        restaurar.getStyleClass().add("peligro");
        restaurar.setOnAction(e -> restaurar());

        VBox contenido = new VBox(14, titulo, nota, new HBox(10, generar, restaurar));
        contenido.setPadding(new Insets(16));
        raiz = new ScrollPane(contenido);
        raiz.setFitToWidth(true);
    }

    public Node nodo() {
        return raiz;
    }

    private void generar() {
        FileChooser selector = selector();
        selector.setInitialFileName("respaldo-" + LocalDate.now() + ".svr");
        File archivo = selector.showSaveDialog(raiz.getScene().getWindow());
        if (archivo == null) {
            return;
        }
        Optional<char[]> contrasena = Dialogos.contrasena("Escribe una contraseña para proteger el respaldo.\n"
                + "La necesitarás para restaurarlo; no se puede recuperar.");
        if (contrasena.isEmpty()) {
            return;
        }
        char[] clave = contrasena.get();
        try {
            Path destino = conExtension(archivo);
            ctx.app().respaldo().generar(destino, clave);
            ctx.cambio(); // la bitacora registra el respaldo
            Dialogos.info("Respaldo guardado en:\n" + destino);
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        } finally {
            Arrays.fill(clave, ' ');
        }
    }

    private void restaurar() {
        File archivo = selector().showOpenDialog(raiz.getScene().getWindow());
        if (archivo == null) {
            return;
        }
        if (!Dialogos.confirmar("Restaurar reemplaza TODOS los datos actuales por los del respaldo.\n"
                + "¿Deseas continuar?", "Restaurar")) {
            return;
        }
        Optional<char[]> contrasena = Dialogos.contrasena("Escribe la contraseña del respaldo.");
        if (contrasena.isEmpty()) {
            return;
        }
        char[] clave = contrasena.get();
        try {
            ctx.app().respaldo().restaurar(archivo.toPath(), clave);
            ctx.app().experiencia().completar();
            ctx.app().cobros().generarTodos();
            ctx.cambio();
            Dialogos.info("Respaldo restaurado.");
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        } finally {
            Arrays.fill(clave, ' ');
        }
    }

    private static FileChooser selector() {
        FileChooser selector = new FileChooser();
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Respaldo SV-Cobranza (*.svr)", "*.svr"));
        return selector;
    }

    private static Path conExtension(File archivo) {
        String nombre = archivo.getName();
        return nombre.toLowerCase().endsWith(".svr") ? archivo.toPath() : archivo.toPath().resolveSibling(nombre + ".svr");
    }
}
