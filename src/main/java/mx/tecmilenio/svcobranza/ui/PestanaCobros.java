package mx.tecmilenio.svcobranza.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.model.EstadoPendiente;
import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.UsuarioAtendido;

/**
 * Pestaña "Cobros pendientes": un renglon plegable por usuario con sus cobros de los proximos 30
 * dias mas los vencidos. Desde ahi se paga, se condona y se revierte una condonacion (los cobros
 * condonados aparecen dentro del renglon de su usuario).
 */
public final class PestanaCobros {

    private final Contexto ctx;
    private final ScrollPane raiz;
    private final VBox lista = new VBox(8);
    private final Set<Long> abiertos = new HashSet<>();

    public PestanaCobros(Contexto ctx) {
        this.ctx = ctx;
        Label titulo = new Label("Cobros pendientes");
        titulo.getStyleClass().add("titulo-seccion");
        Button avisar = new Button("Ver el aviso de hoy");
        avisar.getStyleClass().add("secundario");
        avisar.setOnAction(e -> VentanaAviso.consultar(ctx));
        Label nota = new Label("Cobros vencidos y de los próximos 30 días. Abre el nombre de un usuario para "
                + "pagar, condonar o revertir una condonación.");
        nota.setWrapText(true);
        nota.getStyleClass().add("nota");

        VBox contenido = new VBox(12, new HBox(14, titulo, avisar), nota, lista);
        contenido.setPadding(new Insets(16));
        raiz = new ScrollPane(contenido);
        raiz.setFitToWidth(true);

        ctx.alCambiar(this::refrescar);
        refrescar();
    }

    public Node nodo() {
        return raiz;
    }

    // ---------- lista por usuario ----------

    private void refrescar() {
        LocalDate hoy = LocalDate.now();
        Map<Long, List<Pendiente>> porUsuario = new LinkedHashMap<>();
        for (UsuarioAtendido u : ctx.app().usuarios().listar()) {
            porUsuario.put(u.id(), new ArrayList<>());
        }
        for (Pendiente p : ctx.app().cobros().visibles()) {
            porUsuario.computeIfAbsent(p.usuarioId(), id -> new ArrayList<>()).add(p);
        }
        for (Long id : new ArrayList<>(porUsuario.keySet())) {
            for (Pendiente p : ctx.app().usuarios().cobrosDe(id)) {
                if (p.estado() == EstadoPendiente.CONDONADO) {
                    porUsuario.get(id).add(p);
                }
            }
        }

        lista.getChildren().clear();
        for (List<Pendiente> cobros : porUsuario.values()) {
            if (cobros.isEmpty()) {
                continue;
            }
            cobros.sort(java.util.Comparator.comparing(Pendiente::fechaLimite).thenComparingLong(Pendiente::id));
            lista.getChildren().add(panelDe(cobros, hoy));
        }
        if (lista.getChildren().isEmpty()) {
            Label vacio = new Label("No hay cobros pendientes.");
            vacio.getStyleClass().add("nota");
            lista.getChildren().add(vacio);
        }
    }

    private TitledPane panelDe(List<Pendiente> cobros, LocalDate hoy) {
        Pendiente primero = cobros.get(0);
        long vencidos = cobros.stream().filter(p -> p.vencido(hoy)).count();
        long pendientes = cobros.stream().filter(p -> p.estado() == EstadoPendiente.PENDIENTE).count();
        long condonados = cobros.size() - pendientes;
        BigDecimal total = cobros.stream().filter(p -> p.estado() == EstadoPendiente.PENDIENTE)
                .map(Pendiente::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder resumen = new StringBuilder(primero.usuario()).append("  —  ").append(pendientes)
                .append(pendientes == 1 ? " cobro" : " cobros").append(", ").append(Formatos.moneda(total));
        if (vencidos > 0) {
            resumen.append("  (").append(vencidos).append(vencidos == 1 ? " vencido)" : " vencidos)");
        }
        if (condonados > 0) {
            resumen.append("  ·  ").append(condonados).append(condonados == 1 ? " condonado" : " condonados");
        }

        TitledPane panel = new TitledPane(resumen.toString(), tablaDe(cobros, hoy));
        panel.setExpanded(abiertos.contains(primero.usuarioId()));
        panel.expandedProperty().addListener((o, antes, ahora) -> {
            if (ahora) {
                abiertos.add(primero.usuarioId());
            } else {
                abiertos.remove(primero.usuarioId());
            }
        });
        return panel;
    }

    private Node tablaDe(List<Pendiente> cobros, LocalDate hoy) {
        TableView<Pendiente> tabla = new TableView<>(FXCollections.observableArrayList(cobros));
        tabla.setPrefHeight(Math.min(300, 36.0 + 30.0 * cobros.size()));
        tabla.getColumns().add(columna("Fecha", 95, p -> Formatos.fecha(p.fechaLimite())));
        tabla.getColumns().add(columna("Concepto", 140, Pendiente::categoria));
        tabla.getColumns().add(columna("Monto", 110, p -> Formatos.moneda(p.monto())));
        tabla.getColumns().add(columna("Estado", 330, p -> estado(p, hoy)));
        tabla.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Pendiente p, boolean vacia) {
                super.updateItem(p, vacia);
                getStyleClass().remove("aviso-vencido");
                if (!vacia && p != null && p.vencido(hoy)) {
                    getStyleClass().add("aviso-vencido");
                }
            }
        });

        Button pagar = new Button("Pagar");
        pagar.setOnAction(e -> pagar(tabla.getSelectionModel().getSelectedItem()));
        Button condonar = new Button("Condonar");
        condonar.getStyleClass().add("secundario");
        condonar.setOnAction(e -> condonar(tabla.getSelectionModel().getSelectedItem()));
        Button revertir = new Button("Revertir condonación");
        revertir.getStyleClass().add("secundario");
        revertir.setOnAction(e -> revertir(tabla.getSelectionModel().getSelectedItem()));

        var elegido = tabla.getSelectionModel().selectedItemProperty();
        pagar.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> elegido.get() == null || elegido.get().estado() != EstadoPendiente.PENDIENTE, elegido));
        condonar.disableProperty().bind(pagar.disableProperty());
        revertir.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> elegido.get() == null || elegido.get().estado() != EstadoPendiente.CONDONADO, elegido));

        VBox caja = new VBox(8, tabla, new HBox(10, pagar, condonar, revertir));
        caja.setPadding(new Insets(6));
        return caja;
    }

    private static TableColumn<Pendiente, String> columna(String titulo, double ancho,
            Function<Pendiente, String> valor) {
        TableColumn<Pendiente, String> columna = new TableColumn<>(titulo);
        columna.setPrefWidth(ancho);
        columna.setCellValueFactory(c -> new SimpleStringProperty(valor.apply(c.getValue())));
        return columna;
    }

    private static String estado(Pendiente p, LocalDate hoy) {
        if (p.estado() == EstadoPendiente.CONDONADO) {
            return "Condonado" + (p.motivo() == null ? "" : " — " + p.motivo());
        }
        if (p.vencido(hoy)) {
            return "Vencido (" + Avisos.plazo(java.time.temporal.ChronoUnit.DAYS.between(hoy, p.fechaLimite())) + ")";
        }
        return "Pendiente (" + Avisos.plazo(java.time.temporal.ChronoUnit.DAYS.between(hoy, p.fechaLimite())) + ")";
    }

    // ---------- acciones ----------

    private void pagar(Pendiente p) {
        if (p == null) {
            return;
        }
        DatePicker fecha = Fechas.selector(LocalDate.now());
        ComboBox<FormaPago> forma = new ComboBox<>(FXCollections.observableArrayList(FormaPago.values()));
        forma.setPromptText("Elige una");
        TextField monto = new TextField(p.monto().toPlainString());

        GridPane campos = new GridPane();
        campos.setHgap(10);
        campos.setVgap(8);
        campos.addRow(0, new Label("Fecha de pago"), fecha);
        campos.addRow(1, new Label("Forma de pago"), forma);
        campos.addRow(2, new Label("Monto"), monto);
        Label encabezado = new Label("Pagar " + p.categoria() + " de " + p.usuario() + " (cobro del "
                + Formatos.fecha(p.fechaLimite()) + ")");
        encabezado.setWrapText(true);
        encabezado.setMaxWidth(380);

        ButtonType registrar = new ButtonType("Registrar pago", ButtonBar.ButtonData.OK_DONE);
        Alert dialogo = Dialogos.crear(Alert.AlertType.NONE, "", registrar, Dialogos.CANCELAR);
        dialogo.getDialogPane().setContent(new VBox(12, encabezado, campos));
        Optional<ButtonType> respuesta = dialogo.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != registrar) {
            return;
        }
        try {
            ctx.app().cobros().pagar(p.id(), Entradas.fecha(Fechas.texto(fecha), "pago"), forma.getValue(),
                    Entradas.monto(monto.getText(), "pago"));
            ctx.cambio();
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        }
    }

    private void condonar(Pendiente p) {
        if (p == null) {
            return;
        }
        Optional<String> motivo = Dialogos.texto("Condonar " + p.categoria() + " de " + p.usuario() + " del "
                + Formatos.fecha(p.fechaLimite()) + " (" + Formatos.moneda(p.monto())
                + ").\nNo generará ingreso ni recordatorio. El motivo es opcional.", "Condonar");
        if (motivo.isEmpty()) {
            return;
        }
        try {
            ctx.app().cobros().condonar(p.id(), motivo.get());
            ctx.cambio();
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        }
    }

    private void revertir(Pendiente p) {
        if (p == null) {
            return;
        }
        if (!Dialogos.confirmar("¿Revertir la condonación de " + p.categoria() + " del "
                + Formatos.fecha(p.fechaLimite()) + "? El cobro volverá a estar pendiente.", "Revertir")) {
            return;
        }
        try {
            ctx.app().cobros().revertirCondonacion(p.id());
            ctx.cambio();
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        }
    }
}
