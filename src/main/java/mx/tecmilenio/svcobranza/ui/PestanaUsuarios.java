package mx.tecmilenio.svcobranza.ui;

import java.time.LocalDate;
import java.util.Optional;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.model.UsuarioAtendido;
import mx.tecmilenio.svcobranza.service.ExperienciaService;
import mx.tecmilenio.svcobranza.service.RecordatorioService;

/**
 * Pestaña "Usuarios": lista de usuarios (editar y borrar) y la seccion "Recordatorios/Experiencia",
 * donde se captura y edita el calendario de experiencia y los dias de aviso.
 */
public final class PestanaUsuarios {

    private final Contexto ctx;
    private final ScrollPane raiz;

    // usuarios
    private final TableView<UsuarioAtendido> tabla = new TableView<>();
    private final FormularioUsuario formulario;
    private final VBox editor = new VBox(10);
    private final Label tituloEditor = new Label();

    // experiencia
    private final Label avisoSinFecha = new Label("Aún no hay fecha de experiencia: no se cobrará experiencia a "
            + "nadie hasta que captures la primera fecha aquí.");
    private final HBox capturaPrimera = new HBox(10);
    private final javafx.scene.control.DatePicker primeraFecha = Fechas.selector(null);
    private final ListView<LocalDate> listaFechas = new ListView<>();
    private final Button editarFecha = new Button("Cambiar fecha seleccionada");
    private final Spinner<Integer> diasExperiencia = new Spinner<>(0, RecordatorioService.DIAS_MAXIMOS,
            RecordatorioService.DIAS_EXPERIENCIA_POR_DEFECTO);
    private final Spinner<Integer> diasMensual = new Spinner<>(0, RecordatorioService.DIAS_MAXIMOS,
            RecordatorioService.DIAS_MENSUAL_POR_DEFECTO);

    public PestanaUsuarios(Contexto ctx) {
        this.ctx = ctx;
        this.formulario = new FormularioUsuario(ctx);

        VBox contenido = new VBox(18, seccionUsuarios(), seccionExperiencia());
        contenido.setPadding(new Insets(16));
        raiz = new ScrollPane(contenido);
        raiz.setFitToWidth(true);

        ctx.alCambiar(this::refrescar);
        refrescar();
    }

    public Node nodo() {
        return raiz;
    }

    // ---------- usuarios ----------

    private Node seccionUsuarios() {
        Label titulo = new Label("Usuarios registrados");
        titulo.getStyleClass().add("titulo-seccion");

        tabla.setPlaceholder(new Label("Aún no hay usuarios registrados."));
        tabla.setPrefHeight(260);
        tabla.getColumns().add(columna("Nombre", 220, u -> u.nombre()));
        tabla.getColumns().add(columna("Ingreso", 100, u -> Formatos.fecha(u.fechaIngreso())));
        tabla.getColumns().add(columna("Esquema", 160, u -> u.esquema() == null ? "" : u.esquema()));
        tabla.getColumns().add(columna("Becado", 70, u -> u.becado() ? "Sí" : "No"));
        tabla.getColumns().add(columna("Estancia", 150, PestanaUsuarios::estancia));
        tabla.getColumns().add(columna("Experiencia", 100,
                u -> u.experiencia() ? Formatos.moneda(u.montoExperiencia()) : ""));
        tabla.getColumns().add(columna("Tiendita", 100, u -> Formatos.moneda(u.montoTiendita())));

        Button editar = new Button("Editar");
        editar.setOnAction(e -> editarSeleccionado());
        Button borrar = new Button("Borrar");
        borrar.getStyleClass().add("peligro");
        borrar.setOnAction(e -> borrarSeleccionado());
        editar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        borrar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());

        tituloEditor.getStyleClass().add("titulo-seccion");
        Button guardar = new Button("Guardar cambios");
        guardar.setOnAction(e -> guardarEdicion());
        Button cancelar = new Button("Cancelar");
        cancelar.getStyleClass().add("secundario");
        cancelar.setOnAction(e -> cerrarEditor());
        editor.getChildren().addAll(tituloEditor, formulario.nodo(), new HBox(10, guardar, cancelar));
        editor.setVisible(false);
        editor.setManaged(false);

        return new VBox(10, titulo, tabla, new HBox(10, editar, borrar), editor);
    }

    private static TableColumn<UsuarioAtendido, String> columna(String titulo, double ancho,
            java.util.function.Function<UsuarioAtendido, String> valor) {
        TableColumn<UsuarioAtendido, String> columna = new TableColumn<>(titulo);
        columna.setPrefWidth(ancho);
        columna.setCellValueFactory(c -> new SimpleStringProperty(valor.apply(c.getValue())));
        return columna;
    }

    private static String estancia(UsuarioAtendido u) {
        if (u.becado() || u.frecuenciaEstancia() == null) {
            return "";
        }
        return u.frecuenciaEstancia() + " " + Formatos.moneda(u.montoEstancia());
    }

    private void editarSeleccionado() {
        UsuarioAtendido elegido = tabla.getSelectionModel().getSelectedItem();
        if (elegido == null) {
            return;
        }
        formulario.cargar(elegido);
        tituloEditor.setText("Editando a " + elegido.nombre());
        editor.setVisible(true);
        editor.setManaged(true);
    }

    private void guardarEdicion() {
        try {
            UsuarioAtendido guardado = ctx.app().usuarios().editar(formulario.leer());
            cerrarEditor();
            ctx.cambio();
            Dialogos.info("Se guardaron los cambios de " + guardado.nombre() + ".");
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        }
    }

    private void cerrarEditor() {
        formulario.limpiar();
        editor.setVisible(false);
        editor.setManaged(false);
    }

    private void borrarSeleccionado() {
        UsuarioAtendido elegido = tabla.getSelectionModel().getSelectedItem();
        if (elegido == null) {
            return;
        }
        String pregunta = "¿Borrar a " + elegido.nombre() + "?\n\nSe borrarán también sus cobros pendientes. "
                + "Los ingresos que ya se cobraron se conservan.";
        if (!Dialogos.confirmar(pregunta, "Borrar")) {
            return;
        }
        try {
            ctx.app().usuarios().borrar(elegido.id());
            cerrarEditor();
            ctx.cambio();
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        }
    }

    // ---------- Recordatorios/Experiencia ----------

    private Node seccionExperiencia() {
        Label titulo = new Label("Recordatorios/Experiencia");
        titulo.getStyleClass().add("titulo-seccion");

        avisoSinFecha.setWrapText(true);
        avisoSinFecha.getStyleClass().addAll("panel-aviso", "aviso-experiencia");
        Button capturar = new Button("Capturar primera fecha");
        capturar.setOnAction(e -> capturarPrimeraFecha());
        capturaPrimera.setAlignment(Pos.CENTER_LEFT);
        capturaPrimera.getChildren().addAll(new Label("Primera fecha de experiencia"), primeraFecha, capturar);

        Label nota = new Label("Las fechas se repiten cada " + ExperienciaService.INTERVALO_DIAS
                + " días. Al cambiar una fecha, las siguientes se recorren a " + ExperienciaService.INTERVALO_DIAS
                + " días de la nueva; las anteriores no cambian. Solo se cobra a partir de hoy.");
        nota.setWrapText(true);
        nota.getStyleClass().add("nota");

        listaFechas.setPrefHeight(180);
        listaFechas.setCellFactory(l -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(LocalDate fecha, boolean vacia) {
                super.updateItem(fecha, vacia);
                setText(vacia || fecha == null ? null : Formatos.fecha(fecha));
            }
        });
        editarFecha.setOnAction(e -> cambiarFecha());
        editarFecha.disableProperty().bind(listaFechas.getSelectionModel().selectedItemProperty().isNull());

        diasExperiencia.setEditable(true);
        diasMensual.setEditable(true);
        diasExperiencia.setPrefWidth(80);
        diasMensual.setPrefWidth(80);
        Button guardarDias = new Button("Guardar días de aviso");
        guardarDias.setOnAction(e -> guardarDiasAviso());
        HBox dias = new HBox(10, new Label("Avisar la experiencia con"), diasExperiencia,
                new Label("días de anticipación; los cobros mensuales con"), diasMensual, new Label("días"));
        dias.setAlignment(Pos.CENTER_LEFT);

        return new VBox(10, titulo, avisoSinFecha, capturaPrimera, nota, listaFechas, editarFecha,
                new Label("Días de aviso (de 0 a " + RecordatorioService.DIAS_MAXIMOS + ")"), dias, guardarDias);
    }

    private void capturarPrimeraFecha() {
        try {
            LocalDate fecha = Entradas.fecha(Fechas.texto(primeraFecha), "experiencia");
            ctx.app().experiencia().definirPrimeraFecha(fecha);
            primeraFecha.setValue(null);
            primeraFecha.getEditor().clear();
            ctx.cambio();
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        }
    }

    private void cambiarFecha() {
        LocalDate actual = listaFechas.getSelectionModel().getSelectedItem();
        if (actual == null) {
            return;
        }
        try {
            Optional<LocalDate> nueva = Dialogos.fecha("Nueva fecha para el " + Formatos.fecha(actual)
                    + ".\nLas fechas siguientes se recorrerán a " + ExperienciaService.INTERVALO_DIAS
                    + " días de la nueva.", actual);
            if (nueva.isPresent()) {
                ctx.app().experiencia().editarFecha(actual, nueva.get());
                ctx.cambio();
            }
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        }
    }

    private void guardarDiasAviso() {
        try {
            diasExperiencia.commitValue();
            diasMensual.commitValue();
            ctx.app().recordatorios().definirDiasAviso(diasExperiencia.getValue(), diasMensual.getValue());
            Dialogos.info("Se guardaron los días de aviso.");
        } catch (AppException | NumberFormatException e) {
            Dialogos.error(e instanceof AppException ? e.getMessage()
                    : "Los días de aviso deben ser números enteros de 0 a " + RecordatorioService.DIAS_MAXIMOS + ".");
        }
    }

    // ---------- refresco ----------

    private void refrescar() {
        tabla.setItems(FXCollections.observableArrayList(ctx.app().usuarios().listar()));

        boolean sinFechas = ctx.app().experiencia().sinCalendario();
        for (Node nodo : new Node[] { avisoSinFecha, capturaPrimera }) {
            nodo.setVisible(sinFechas);
            nodo.setManaged(sinFechas);
        }
        listaFechas.setItems(FXCollections.observableArrayList(ctx.app().experiencia().fechas()));

        diasExperiencia.getValueFactory().setValue(ctx.app().recordatorios().diasAvisoExperiencia());
        diasMensual.getValueFactory().setValue(ctx.app().recordatorios().diasAvisoMensual());
    }
}
