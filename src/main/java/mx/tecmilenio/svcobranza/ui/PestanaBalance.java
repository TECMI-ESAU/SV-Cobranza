package mx.tecmilenio.svcobranza.ui;

import java.io.File;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.model.Balance;
import mx.tecmilenio.svcobranza.model.ReporteUsuario;
import mx.tecmilenio.svcobranza.model.UsuarioAtendido;

/**
 * Pestaña Balance: ingresos menos egresos dentro de un rango Desde-Hasta (acordado en la segunda
 * reunion con el cliente), con exportacion a Excel y reporte por usuario.
 */
public final class PestanaBalance {

    private final Contexto ctx;
    private final ScrollPane raiz;

    private final DatePicker desde = Fechas.selector(null);
    private final DatePicker hasta = Fechas.selector(null);
    private final Label ingresos = new Label();
    private final Label egresos = new Label();
    private final Label saldo = new Label();
    private final Label aviso = new Label();
    private final TableView<Fila> tabla = new TableView<>();
    private final ComboBox<String> usuario = new ComboBox<>();
    private Balance actual;

    /** Un renglon del detalle por categoria. */
    public record Fila(String tipo, String categoria, String monto, String porcentaje) {
    }

    public PestanaBalance(Contexto ctx) {
        this.ctx = ctx;

        desde.setPrefWidth(140);
        hasta.setPrefWidth(140);
        Button calcular = new Button("Calcular");
        calcular.setOnAction(e -> calcular());
        Button semana = new Button("Semana actual");
        semana.getStyleClass().add("secundario");
        semana.setOnAction(e -> {
            LocalDate hoy = LocalDate.now();
            desde.setValue(hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            hasta.setValue(hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
            calcular();
        });
        Button exportar = new Button("Exportar balance a Excel...");
        exportar.getStyleClass().add("secundario");
        exportar.setOnAction(e -> exportarBalance());
        HBox rango = new HBox(10, new Label("Desde"), desde, new Label("Hasta"), hasta, calcular, semana, exportar);
        rango.setAlignment(Pos.CENTER_LEFT);

        for (Label etiqueta : new Label[] { ingresos, egresos, saldo }) {
            etiqueta.getStyleClass().add("titulo-seccion");
        }
        HBox totales = new HBox(30, par("Ingresos", ingresos), par("Egresos", egresos), par("Saldo", saldo));
        aviso.getStyleClass().addAll("panel-aviso", "aviso-vencido");
        aviso.setWrapText(true);
        mostrarAviso(false);

        tabla.setPlaceholder(new Label("Elige un rango y pulsa Calcular."));
        tabla.setPrefHeight(300);
        tabla.getColumns().add(columna("Tipo", 100, Fila::tipo));
        tabla.getColumns().add(columna("Categoría", 220, Fila::categoria));
        tabla.getColumns().add(columna("Monto", 150, Fila::monto));
        tabla.getColumns().add(columna("Porcentaje", 110, Fila::porcentaje));

        Label tituloUsuario = new Label("Reporte por usuario");
        tituloUsuario.getStyleClass().add("titulo-seccion");
        usuario.setPromptText("Elige un usuario");
        Button reporte = new Button("Exportar reporte del usuario a Excel...");
        reporte.getStyleClass().add("secundario");
        reporte.setOnAction(e -> exportarReporte());
        reporte.disableProperty().bind(usuario.valueProperty().isNull());

        Label titulo = new Label("Balance");
        titulo.getStyleClass().add("titulo-seccion");
        VBox contenido = new VBox(14, titulo, rango, totales, aviso, tabla, tituloUsuario,
                new HBox(10, usuario, reporte));
        contenido.setPadding(new Insets(16));
        raiz = new ScrollPane(contenido);
        raiz.setFitToWidth(true);

        ctx.alCambiar(this::refrescar);
        semana.fire();
        cargarUsuarios();
    }

    public Node nodo() {
        return raiz;
    }

    // ---------- balance ----------

    private void calcular() {
        try {
            LocalDate d = Entradas.fecha(Fechas.texto(desde), "inicio del balance");
            LocalDate h = Entradas.fecha(Fechas.texto(hasta), "fin del balance");
            mostrar(ctx.app().balance().calcular(d, h));
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        }
    }

    private void mostrar(Balance b) {
        actual = b;
        ingresos.setText(Formatos.moneda(b.totalIngresos()));
        egresos.setText(Formatos.moneda(b.totalEgresos()));
        saldo.setText(Formatos.moneda(b.saldo()));
        aviso.setText("Atención: los egresos superan a los ingresos del periodo.");
        mostrarAviso(b.egresosSuperanIngresos());
        tabla.setItems(FXCollections.observableArrayList(filas(b)));
    }

    /** Detalle por categoria: primero los ingresos y luego los egresos. */
    static List<Fila> filas(Balance b) {
        List<Fila> filas = new ArrayList<>();
        agregar(filas, "Ingreso", b.ingresosPorCategoria(), b.porcentajesIngresos());
        agregar(filas, "Egreso", b.egresosPorCategoria(), b.porcentajesEgresos());
        return filas;
    }

    private static void agregar(List<Fila> filas, String tipo, Map<String, BigDecimal> montos,
            Map<String, BigDecimal> porcentajes) {
        montos.forEach((categoria, monto) -> filas.add(new Fila(tipo, categoria, Formatos.moneda(monto),
                porcentajes.getOrDefault(categoria, BigDecimal.ZERO) + " %")));
    }

    private void mostrarAviso(boolean ver) {
        aviso.setVisible(ver);
        aviso.setManaged(ver);
    }

    /** Se llama cuando cambian los datos desde otra pestaña: recalcula el rango que se esta viendo. */
    private void refrescar() {
        cargarUsuarios();
        if (actual != null) {
            mostrar(ctx.app().balance().calcular(actual.desde(), actual.hasta()));
        }
    }

    private void cargarUsuarios() {
        List<String> nombres = new ArrayList<>();
        for (UsuarioAtendido u : ctx.app().usuarios().listar()) {
            nombres.add(u.nombre());
        }
        String elegido = usuario.getValue();
        usuario.setItems(FXCollections.observableArrayList(nombres));
        usuario.setValue(nombres.contains(elegido) ? elegido : null);
    }

    // ---------- Excel ----------

    private void exportarBalance() {
        if (actual == null) {
            Dialogos.info("Primero calcula el balance.");
            return;
        }
        Path destino = elegirArchivo("balance-" + LocalDate.now() + ".xlsx");
        if (destino == null) {
            return;
        }
        try {
            ctx.app().excel().exportarBalance(actual, destino);
            Dialogos.info("Archivo guardado en:\n" + destino);
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        }
    }

    private void exportarReporte() {
        try {
            ReporteUsuario reporte = ctx.app().balance().reportePorUsuario(usuario.getValue());
            Path destino = elegirArchivo("reporte-" + reporte.usuario().replaceAll("[^\\p{L}\\p{N}]+", "-") + "-"
                    + LocalDate.now() + ".xlsx");
            if (destino == null) {
                return;
            }
            ctx.app().excel().exportarReporteUsuario(reporte, destino);
            Dialogos.info("Archivo guardado en:\n" + destino);
        } catch (AppException e) {
            Dialogos.error(e.getMessage());
        }
    }

    private Path elegirArchivo(String sugerido) {
        FileChooser selector = new FileChooser();
        selector.setInitialFileName(sugerido);
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));
        File archivo = selector.showSaveDialog(raiz.getScene().getWindow());
        if (archivo == null) {
            return null;
        }
        String nombre = archivo.getName().toLowerCase().endsWith(".xlsx") ? archivo.getName()
                : archivo.getName() + ".xlsx";
        return archivo.toPath().resolveSibling(nombre);
    }

    // ---------- ayudas ----------

    private static HBox par(String titulo, Label valor) {
        HBox caja = new HBox(8, new Label(titulo + ":"), valor);
        caja.setAlignment(Pos.CENTER_LEFT);
        return caja;
    }

    private static TableColumn<Fila, String> columna(String titulo, double ancho,
            java.util.function.Function<Fila, String> valor) {
        TableColumn<Fila, String> columna = new TableColumn<>(titulo);
        columna.setPrefWidth(ancho);
        columna.setCellValueFactory(c -> new SimpleStringProperty(valor.apply(c.getValue())));
        return columna;
    }
}
