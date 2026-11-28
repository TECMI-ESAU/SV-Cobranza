package mx.tecmilenio.svcobranza.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.model.Categorias;
import mx.tecmilenio.svcobranza.model.FiltroMovimiento;
import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Movimiento;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;
import mx.tecmilenio.svcobranza.model.UsuarioAtendido;
import mx.tecmilenio.svcobranza.service.PosibleDuplicadoException;

/**
 * Pestaña Ingresos o Egresos (la misma pantalla, segun el tipo): captura, edicion, cancelacion y
 * busqueda. Solo lee y muestra datos; las reglas las valida {@code MovimientoService}.
 */
public final class PestanaMovimientos {

    private final Contexto ctx;
    private final TipoMovimiento tipo;
    private final ScrollPane raiz;

    private long idEnEdicion;
    private final Label tituloFormulario = new Label();
    private final DatePicker fecha = Fechas.selector(LocalDate.now());
    private final TextField monto = new TextField();
    private final ComboBox<String> categoria = new ComboBox<>();
    private final ComboBox<String> usuario = new ComboBox<>();
    private final HBox filaUsuario;
    private final TextField concepto = new TextField();
    private final ComboBox<FormaPago> forma = new ComboBox<>(FXCollections.observableArrayList(FormaPago.values()));
    private final HBox filaForma;

    private final TextField buscaUsuario = new TextField();
    private final TextField buscaConcepto = new TextField();
    private final DatePicker buscaDesde = Fechas.selector(null);
    private final DatePicker buscaHasta = Fechas.selector(null);
    private final CheckBox conCancelados = new CheckBox("Incluir cancelados");
    private final TableView<Movimiento> tabla = new TableView<>();

    public PestanaMovimientos(Contexto ctx, TipoMovimiento tipo) {
        this.ctx = ctx;
        this.tipo = tipo;
        boolean ingreso = tipo == TipoMovimiento.INGRESO;

        categoria.setItems(FXCollections.observableArrayList(ctx.app().movimientos().categorias(tipo)));
        categoria.setPromptText("Elige una");
        monto.setPromptText("0.00");
        concepto.setPromptText(ingreso ? "Obligatorio" : "Opcional");
        forma.setPromptText("Elige una");
        categoria.valueProperty().addListener((o, antes, ahora) -> actualizarUsuario());

        filaUsuario = fila(ingreso ? "Usuario (obligatorio salvo en Otros)" : "Usuario que recibe la donación",
                usuario);
        filaForma = fila("Forma de pago", forma);
        filaForma.setVisible(ingreso);
        filaForma.setManaged(ingreso);

        tituloFormulario.getStyleClass().add("titulo-seccion");
        Button guardar = new Button("Guardar");
        guardar.setOnAction(e -> guardar());
        Button limpiar = new Button("Limpiar");
        limpiar.getStyleClass().add("secundario");
        limpiar.setOnAction(e -> limpiarFormulario());

        VBox formulario = new VBox(8, tituloFormulario, fila("Fecha", fecha), fila("Monto", monto),
                fila("Categoría", categoria), filaUsuario, fila(ingreso ? "Concepto" : "Descripción", concepto),
                filaForma, new HBox(10, guardar, limpiar));

        VBox contenido = new VBox(18, formulario, seccionLista());
        contenido.setPadding(new Insets(16));
        raiz = new ScrollPane(contenido);
        raiz.setFitToWidth(true);

        ctx.alCambiar(this::refrescar);
        limpiarFormulario();
        refrescar();
    }

    public Node nodo() {
        return raiz;
    }

    // ---------- formulario ----------

    private void actualizarUsuario() {
        boolean ingreso = tipo == TipoMovimiento.INGRESO;
        boolean lleva = ingreso || Categorias.DONACION.equals(categoria.getValue());
        filaUsuario.setVisible(lleva);
        filaUsuario.setManaged(lleva);
        if (!lleva) {
            usuario.setValue("");
        }
    }

    private void cargarUsuarios() {
        List<String> nombres = new ArrayList<>();
        nombres.add("");
        for (UsuarioAtendido u : ctx.app().usuarios().listar()) {
            nombres.add(u.nombre());
        }
        String actual = usuario.getValue();
        usuario.setItems(FXCollections.observableArrayList(nombres));
        usuario.setValue(actual == null ? "" : actual);
    }

    private void limpiarFormulario() {
        idEnEdicion = 0;
        tituloFormulario.setText(tipo == TipoMovimiento.INGRESO ? "Registrar un ingreso" : "Registrar un egreso");
        fecha.setValue(LocalDate.now());
        monto.clear();
        categoria.setValue(null);
        usuario.setValue("");
        concepto.clear();
        forma.setValue(null);
        actualizarUsuario();
    }

    private void cargar(Movimiento m) {
        limpiarFormulario();
        idEnEdicion = m.id();
        tituloFormulario.setText("Editando " + tipo.etiqueta().toLowerCase() + " del " + Formatos.fecha(m.fecha()));
        fecha.setValue(m.fecha());
        monto.setText(m.monto().toPlainString());
        categoria.setValue(m.categoria());
        if (m.usuario() != null && !usuario.getItems().contains(m.usuario())) {
            usuario.getItems().add(m.usuario()); // usuario dado de baja: se conserva en su historial
        }
        usuario.setValue(m.usuario() == null ? "" : m.usuario());
        concepto.setText(m.concepto() == null ? "" : m.concepto());
        forma.setValue(m.formaPago());
    }

    private Movimiento leer() {
        LocalDate dia = Entradas.fecha(Fechas.texto(fecha), "el " + tipo.etiqueta().toLowerCase());
        BigDecimal importe = Entradas.monto(monto.getText(), tipo.etiqueta().toLowerCase());
        String nombre = usuario.getValue() == null || usuario.getValue().isBlank() ? null : usuario.getValue();
        return new Movimiento(idEnEdicion, tipo, dia, importe, concepto.getText(), categoria.getValue(), nombre,
                tipo == TipoMovimiento.INGRESO ? forma.getValue() : null, false);
    }

    private void guardar() {
        try {
            Movimiento m = leer();
            if (idEnEdicion != 0) {
                ctx.app().movimientos().editar(m);
            } else {
                Movimiento guardado = registrar(m);
                if (guardado == null) {
                    return; // el usuario no quiso guardar un posible duplicado
                }
                preguntarSiLiquida(guardado);
            }
            limpiarFormulario();
            ctx.cambio();
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        }
    }

    /** Guarda el movimiento; si parece duplicado pregunta, y devuelve null si se decide no guardarlo. */
    private Movimiento registrar(Movimiento m) {
        try {
            return ctx.app().movimientos().registrar(m, false);
        } catch (PosibleDuplicadoException e) {
            Movimiento p = e.parecidos().get(0);
            String mensaje = "Ya existe un " + tipo.etiqueta().toLowerCase() + " con la misma fecha y monto"
                    + (p.concepto() == null || p.concepto().isEmpty() ? "" : " (" + p.concepto() + ")")
                    + ".\n\n¿Es un duplicado?";
            if (Dialogos.confirmar(mensaje, "Guardar de todos modos", "No guardar")) {
                return ctx.app().movimientos().registrar(m, true);
            }
            return null;
        }
    }

    /** Un ingreso a mano que coincide con un cobro pendiente puede liquidarlo, si el usuario confirma. */
    private void preguntarSiLiquida(Movimiento guardado) {
        Optional<Pendiente> cobro = ctx.app().cobros().cobroQueLiquida(guardado);
        if (cobro.isPresent() && Dialogos.confirmar("¿Este pago liquida el cobro de " + cobro.get().categoria()
                + " del " + Formatos.fecha(cobro.get().fechaLimite()) + "?", "Sí, liquidarlo", "No")) {
            ctx.app().cobros().liquidar(cobro.get().id(), guardado.id());
        }
    }

    // ---------- lista y busqueda ----------

    private Node seccionLista() {
        Label titulo = new Label(tipo == TipoMovimiento.INGRESO ? "Ingresos registrados" : "Egresos registrados");
        titulo.getStyleClass().add("titulo-seccion");

        buscaUsuario.setPromptText("Usuario");
        buscaConcepto.setPromptText(tipo == TipoMovimiento.INGRESO ? "Concepto" : "Descripción");
        buscaDesde.setPrefWidth(130);
        buscaHasta.setPrefWidth(130);
        Button buscar = new Button("Buscar");
        buscar.setOnAction(e -> aplicarBusqueda());
        Button todo = new Button("Ver todo");
        todo.getStyleClass().add("secundario");
        todo.setOnAction(e -> {
            buscaUsuario.clear();
            buscaConcepto.clear();
            buscaDesde.setValue(null);
            buscaDesde.getEditor().clear();
            buscaHasta.setValue(null);
            buscaHasta.getEditor().clear();
            conCancelados.setSelected(false);
            refrescar();
        });
        HBox busqueda = new HBox(8, buscaUsuario, buscaConcepto, new Label("Desde"), buscaDesde,
                new Label("Hasta"), buscaHasta, conCancelados, buscar, todo);
        busqueda.setAlignment(Pos.CENTER_LEFT);

        tabla.setPlaceholder(new Label("No hay movimientos con esos criterios."));
        tabla.setPrefHeight(320);
        tabla.getColumns().add(columna("Fecha", 95, m -> Formatos.fecha(m.fecha())));
        tabla.getColumns().add(columna("Monto", 110, m -> Formatos.moneda(m.monto())));
        tabla.getColumns().add(columna("Categoría", 150, Movimiento::categoria));
        tabla.getColumns().add(columna("Usuario", 170, Movimiento::persona));
        tabla.getColumns().add(columna(tipo == TipoMovimiento.INGRESO ? "Concepto" : "Descripción", 240,
                m -> m.concepto() == null ? "" : m.concepto()));
        if (tipo == TipoMovimiento.INGRESO) {
            tabla.getColumns().add(columna("Forma de pago", 120,
                    m -> m.formaPago() == null ? "" : m.formaPago().toString()));
        }
        tabla.getColumns().add(columna("Estado", 90, m -> m.cancelado() ? "Cancelado" : ""));

        Button editar = new Button("Editar");
        editar.setOnAction(e -> {
            Movimiento elegido = tabla.getSelectionModel().getSelectedItem();
            if (elegido != null) {
                cargar(elegido);
            }
        });
        Button cancelar = new Button("Cancelar " + tipo.etiqueta().toLowerCase());
        cancelar.getStyleClass().add("peligro");
        cancelar.setOnAction(e -> cancelarSeleccionado());
        editar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        cancelar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());

        return new VBox(10, titulo, busqueda, tabla, new HBox(10, editar, cancelar));
    }

    private TableColumn<Movimiento, String> columna(String titulo, double ancho, Function<Movimiento, String> valor) {
        TableColumn<Movimiento, String> columna = new TableColumn<>(titulo);
        columna.setPrefWidth(ancho);
        columna.setCellValueFactory(c -> new SimpleStringProperty(valor.apply(c.getValue())));
        return columna;
    }

    private void cancelarSeleccionado() {
        Movimiento elegido = tabla.getSelectionModel().getSelectedItem();
        if (elegido == null) {
            return;
        }
        String pregunta = "¿Cancelar el " + tipo.etiqueta().toLowerCase() + " del " + Formatos.fecha(elegido.fecha())
                + " por " + Formatos.moneda(elegido.monto()) + "?\n\nSe conserva en la bitácora, pero deja de contar en el balance.";
        if (!Dialogos.confirmar(pregunta, "Cancelar " + tipo.etiqueta().toLowerCase(), "Volver")) {
            return;
        }
        try {
            ctx.app().movimientos().cancelar(elegido.id());
            if (idEnEdicion == elegido.id()) {
                limpiarFormulario();
            }
            ctx.cambio();
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        }
    }

    private void aplicarBusqueda() {
        try {
            LocalDate desde = Entradas.fecha(Fechas.texto(buscaDesde), "inicio de la búsqueda");
            LocalDate hasta = Entradas.fecha(Fechas.texto(buscaHasta), "fin de la búsqueda");
            tabla.setItems(FXCollections.observableArrayList(ctx.app().movimientos().buscar(
                    new FiltroMovimiento(tipo, texto(buscaUsuario), texto(buscaConcepto), desde, hasta, null, null,
                            conCancelados.isSelected()))));
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        }
    }

    private static String texto(TextField campo) {
        String t = campo.getText();
        return t == null || t.isBlank() ? null : t.trim();
    }

    private void refrescar() {
        cargarUsuarios();
        aplicarBusqueda();
    }

    private static HBox fila(String etiqueta, Node campo) {
        Label texto = new Label(etiqueta);
        texto.setMinWidth(260);
        HBox fila = new HBox(10, texto, campo);
        fila.setAlignment(Pos.CENTER_LEFT);
        if (campo instanceof TextField) {
            ((TextField) campo).setMaxWidth(420);
            ((TextField) campo).setPrefWidth(420);
        }
        return fila;
    }
}
