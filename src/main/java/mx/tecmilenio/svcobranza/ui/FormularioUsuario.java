package mx.tecmilenio.svcobranza.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import mx.tecmilenio.svcobranza.model.Frecuencia;
import mx.tecmilenio.svcobranza.model.Servicio;
import mx.tecmilenio.svcobranza.model.TipoServicio;
import mx.tecmilenio.svcobranza.model.UsuarioAtendido;

/**
 * Campos de un usuario atendido, para el alta (Nuevo Usuario) y para editarlo (Usuarios).
 * Solo lee y muestra datos: todas las validaciones las hace {@code UsuarioService}, y la
 * pantalla muestra su mensaje tal cual. Un usuario becado oculta todo lo que se cobra.
 */
public final class FormularioUsuario {

    public static final String NOTA_TIENDITA = "Los siguientes montos de tiendita deberán ser registrados "
            + "en ingresos bajo el nombre de este usuario.";

    private static final double ANCHO_ETIQUETA = 230;

    private final Contexto ctx;
    private final VBox raiz = new VBox(14);

    private long id;
    private LocalDate fechaRegistro;

    private final TextField nombre = new TextField();
    private final DatePicker ingreso = Fechas.selectorHastaHoy(LocalDate.now());
    private final TextField esquema = new TextField();
    private final CheckBox becado = new CheckBox("Becado (no se cobra estancia, servicios ni experiencia)");
    private final TextField tiendita = new TextField();

    private final ComboBox<Frecuencia> frecuencia = new ComboBox<>(
            FXCollections.observableArrayList(Frecuencia.values()));
    private final TextField montoEstancia = new TextField();
    private final DatePicker fechaBase = Fechas.selector(null);
    private final ComboBox<Integer> diaMes = new ComboBox<>();

    private final CheckBox experiencia = new CheckBox("Experiencia");
    private final TextField montoExperiencia = new TextField();
    private final Label avisoExperiencia = new Label("Aún no hay fecha de experiencia: no se cobrará experiencia "
            + "a nadie hasta que captures la primera fecha en la pestaña Usuarios.");

    private final CampoServicio medico = new CampoServicio(TipoServicio.MEDICO);
    private final CampoServicio psicologo = new CampoServicio(TipoServicio.PSICOLOGO);

    public FormularioUsuario(Contexto ctx) {
        this.ctx = ctx;
        armar();
        limpiar();
    }

    public Node nodo() {
        return raiz;
    }

    // ---------- armado ----------

    private void armar() {
        nombre.setPromptText("Nombre completo");
        esquema.setPromptText("Texto libre (opcional)");
        tiendita.setPromptText("0.00");
        montoEstancia.setPromptText("0.00");
        montoExperiencia.setPromptText("0.00");
        for (int dia = 1; dia <= 31; dia++) {
            diaMes.getItems().add(dia);
        }
        frecuencia.setPromptText("Elige una");
        fechaBase.setPrefWidth(150);

        VBox generales = new VBox(8, fila("Nombre", nombre), fila("Fecha de ingreso", ingreso),
                fila("Esquema de internamiento", esquema), becado);
        VBox filaTiendita = new VBox(4, fila("Monto de tiendita (opcional)", tiendita), nota(NOTA_TIENDITA));

        HBox filaBase = fila("Cobro semanal desde (cada 7 días)", fechaBase);
        HBox filaDia = fila("Día del mes del cobro", diaMes);
        mostrarSi(filaBase, frecuencia.valueProperty().isEqualTo(Frecuencia.SEMANAL));
        mostrarSi(filaDia, frecuencia.valueProperty().isEqualTo(Frecuencia.MENSUAL));
        VBox estancia = new VBox(8, titulo("Estancia"), fila("Se cobra", frecuencia),
                fila("Monto de la estancia", montoEstancia), filaBase, filaDia);

        montoExperiencia.disableProperty().bind(experiencia.selectedProperty().not());
        avisoExperiencia.setWrapText(true);
        avisoExperiencia.getStyleClass().addAll("panel-aviso", "aviso-experiencia");
        experiencia.selectedProperty().addListener((o, antes, ahora) -> actualizarAviso());
        VBox experienciaCaja = new VBox(8, titulo("Experiencia"),
                fila("Participa en experiencia", experiencia), fila("Monto de la experiencia", montoExperiencia),
                avisoExperiencia);

        VBox servicios = new VBox(8, titulo("Servicios (sin fecha de inicio no se cobran)"), medico.nodo(),
                psicologo.nodo());

        VBox seccionCobro = new VBox(14, estancia, experienciaCaja, servicios);
        mostrarSi(seccionCobro, becado.selectedProperty().not());

        raiz.setPadding(new Insets(4));
        raiz.getChildren().addAll(generales, filaTiendita, seccionCobro);
    }

    /** Muestra u oculta el recordatorio de capturar la primera fecha de experiencia. */
    public void actualizarAviso() {
        boolean falta = experiencia.isSelected() && ctx.app().experiencia().sinCalendario();
        avisoExperiencia.setVisible(falta);
        avisoExperiencia.setManaged(falta);
    }

    // ---------- leer y mostrar ----------

    /**
     * Arma el usuario con lo escrito. Lanza AppException si una fecha o un monto esta mal
     * escrito; el resto de las reglas las valida el servicio al guardar.
     */
    public UsuarioAtendido leer() {
        boolean esBecado = becado.isSelected();
        LocalDate fechaIngreso = Entradas.fecha(Fechas.texto(ingreso), "ingreso");
        BigDecimal montoTiendita = Entradas.monto(tiendita.getText(), "tiendita");
        if (esBecado) {
            return new UsuarioAtendido(id, nombre.getText(), fechaIngreso, fechaRegistro, esquema.getText(), true,
                    null, null, null, null, false, null, montoTiendita, List.of());
        }
        Frecuencia frecuenciaEstancia = frecuencia.getValue();
        BigDecimal montoDeEstancia = Entradas.monto(montoEstancia.getText(), "estancia");
        LocalDate base = frecuenciaEstancia == Frecuencia.SEMANAL
                ? Entradas.fecha(Fechas.texto(fechaBase), "inicio del cobro semanal") : null;
        Integer dia = frecuenciaEstancia == Frecuencia.MENSUAL ? diaMes.getValue() : null;
        boolean conExperiencia = experiencia.isSelected();
        BigDecimal montoDeExperiencia = conExperiencia
                ? Entradas.monto(montoExperiencia.getText(), "experiencia") : null;
        List<Servicio> servicios = new ArrayList<>();
        medico.leer().ifPresent(servicios::add);
        psicologo.leer().ifPresent(servicios::add);
        return new UsuarioAtendido(id, nombre.getText(), fechaIngreso, fechaRegistro, esquema.getText(), false,
                frecuenciaEstancia, montoDeEstancia, base, dia, conExperiencia, montoDeExperiencia, montoTiendita,
                servicios);
    }

    /** Muestra los datos de un usuario existente, para editarlo. */
    public void cargar(UsuarioAtendido u) {
        limpiar();
        id = u.id();
        fechaRegistro = u.fechaRegistro();
        nombre.setText(u.nombre());
        ingreso.setValue(u.fechaIngreso());
        esquema.setText(u.esquema() == null ? "" : u.esquema());
        becado.setSelected(u.becado());
        tiendita.setText(texto(u.montoTiendita()));
        frecuencia.setValue(u.frecuenciaEstancia());
        montoEstancia.setText(texto(u.montoEstancia()));
        fechaBase.setValue(u.fechaBase());
        diaMes.setValue(u.diaMes() == null ? UsuarioServiceDefaults.DIA : u.diaMes());
        experiencia.setSelected(u.experiencia());
        montoExperiencia.setText(texto(u.montoExperiencia()));
        medico.cargar(u.servicio(TipoServicio.MEDICO));
        psicologo.cargar(u.servicio(TipoServicio.PSICOLOGO));
        actualizarAviso();
    }

    /** Deja el formulario como para un usuario nuevo. */
    public void limpiar() {
        id = 0;
        fechaRegistro = null;
        nombre.clear();
        ingreso.setValue(LocalDate.now());
        esquema.clear();
        becado.setSelected(false);
        tiendita.clear();
        frecuencia.setValue(null);
        montoEstancia.clear();
        fechaBase.setValue(null);
        fechaBase.getEditor().clear();
        diaMes.setValue(UsuarioServiceDefaults.DIA);
        experiencia.setSelected(false);
        montoExperiencia.clear();
        medico.limpiar();
        psicologo.limpiar();
        actualizarAviso();
    }

    // ---------- ayudas de diseño ----------

    private static String texto(BigDecimal monto) {
        return monto == null ? "" : monto.toPlainString();
    }

    private static HBox fila(String etiqueta, Node campo) {
        Label texto = new Label(etiqueta);
        texto.setMinWidth(ANCHO_ETIQUETA);
        HBox fila = new HBox(10, texto, campo);
        fila.setAlignment(Pos.CENTER_LEFT);
        if (campo instanceof TextField) {
            HBox.setHgrow(campo, Priority.ALWAYS);
            ((TextField) campo).setMaxWidth(420);
        }
        return fila;
    }

    private static Label titulo(String texto) {
        Label titulo = new Label(texto);
        titulo.getStyleClass().add("titulo-seccion");
        return titulo;
    }

    private static Label nota(String texto) {
        Label nota = new Label(texto);
        nota.setWrapText(true);
        nota.getStyleClass().add("nota");
        return nota;
    }

    private static void mostrarSi(Node nodo, javafx.beans.value.ObservableValue<? extends Boolean> condicion) {
        nodo.visibleProperty().bind(condicion);
        nodo.managedProperty().bind(nodo.visibleProperty());
    }

    /** Dia del mes que propone el formulario (el mismo que usa el servicio). */
    private static final class UsuarioServiceDefaults {
        static final int DIA = mx.tecmilenio.svcobranza.service.UsuarioService.DIA_MES_POR_DEFECTO;

        private UsuarioServiceDefaults() {
        }
    }

    // ---------- un servicio (Médico o Psicólogo) ----------

    private static final class CampoServicio {

        private final TipoServicio tipo;
        private final CheckBox activo;
        private final TextField monto = new TextField();
        private final DatePicker inicio = Fechas.selector(null);
        private final ComboBox<Frecuencia> recurrencia = new ComboBox<>(
                FXCollections.observableArrayList(Frecuencia.values()));
        private final HBox caja;

        CampoServicio(TipoServicio tipo) {
            this.tipo = tipo;
            this.activo = new CheckBox(tipo.toString());
            activo.setMinWidth(ANCHO_ETIQUETA);
            monto.setPromptText("Monto");
            monto.setPrefWidth(110);
            inicio.setPrefWidth(150);
            inicio.setPromptText("Inicio (dd/mm/aaaa)");
            for (Node campo : new Node[] { monto, inicio, recurrencia }) {
                campo.disableProperty().bind(activo.selectedProperty().not());
            }
            caja = new HBox(10, activo, monto, inicio, recurrencia);
            caja.setAlignment(Pos.CENTER_LEFT);
            limpiar();
        }

        Node nodo() {
            return caja;
        }

        Optional<Servicio> leer() {
            if (!activo.isSelected()) {
                return Optional.empty();
            }
            return Optional.of(new Servicio(tipo, Entradas.monto(monto.getText(), "servicio " + tipo),
                    Entradas.fecha(Fechas.texto(inicio), "inicio del servicio " + tipo), recurrencia.getValue()));
        }

        void cargar(Servicio servicio) {
            limpiar();
            if (servicio == null) {
                return;
            }
            activo.setSelected(true);
            monto.setText(texto(servicio.monto()));
            inicio.setValue(servicio.fechaInicio());
            recurrencia.setValue(servicio.recurrencia());
        }

        void limpiar() {
            activo.setSelected(false);
            monto.clear();
            inicio.setValue(null);
            inicio.getEditor().clear();
            recurrencia.setValue(Frecuencia.MENSUAL);
        }
    }
}
