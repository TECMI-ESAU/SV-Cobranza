package mx.tecmilenio.svcobranza.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;
import mx.tecmilenio.svcobranza.model.TipoPendiente;
import mx.tecmilenio.svcobranza.service.MovimientoService;
import mx.tecmilenio.svcobranza.service.PendienteService;

/** Pantalla de pagos pendientes (RF-PEN-01, RF-PEN-02 y RF-PEN-04). */
class PanelPendientes extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String TODAS = "Todas las categorías";

    private final PendienteService servicio;
    private final MovimientoService movimientos;
    private final Runnable alCambiar;

    private final JComboBox<TipoPendiente> tipo = new JComboBox<>(TipoPendiente.values());
    private final JTextField limite = new JTextField(Formatos.fecha(LocalDate.now().plusDays(7)));
    private final JTextField monto = new JTextField();
    private final JTextField concepto = new JTextField();
    private final JComboBox<String> categoria = new JComboBox<>();
    private final JTextField contraparte = new JTextField();
    private final JLabel etiquetaContraparte = new JLabel("Usuario atendido");

    private final JComboBox<String> filtroCategoria = new JComboBox<>();
    private final DefaultTableModel modelo = Pantalla.modelo("Fecha límite", "Tipo", "Persona", "Concepto",
            "Categoría", "Monto", "Estado");
    private final JTable tabla = Pantalla.tabla(modelo);
    private List<Pendiente> mostrados = List.of();

    PanelPendientes(PendienteService servicio, MovimientoService movimientos, Runnable alCambiar) {
        super(new BorderLayout());
        this.servicio = servicio;
        this.movimientos = movimientos;
        this.alCambiar = alCambiar;

        add(armarFormulario(), BorderLayout.WEST);
        JPanel centro = new JPanel(new BorderLayout());
        centro.add(armarFiltro(), BorderLayout.NORTH);
        centro.add(new JScrollPane(tabla), BorderLayout.CENTER);
        JButton pagar = new JButton("Marcar como pagado");
        pagar.addActionListener(e -> marcarPagado());
        centro.add(Pantalla.botones(pagar), BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);

        tabla.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getTableCellRendererComponent(JTable t, Object valor, boolean sel,
                    boolean foco, int fila, int columna) {
                java.awt.Component c = super.getTableCellRendererComponent(t, valor, sel, foco, fila, columna);
                if (!sel) {
                    String estado = String.valueOf(t.getValueAt(fila, 6));
                    c.setForeground(estado.startsWith("VENCIDO") ? new Color(180, 0, 0)
                            : estado.equals("Pagado") ? new Color(0, 110, 0) : Color.BLACK);
                }
                return c;
            }
        });

        tipo.addActionListener(e -> alCambiarTipo());
        alCambiarTipo();
        recargarFiltro();
        buscar();
    }

    private JPanel armarFormulario() {
        JPanel form = Pantalla.formulario();
        form.setBorder(new TitledBorder("Registrar pago pendiente"));
        int fila = 0;
        Pantalla.campo(form, fila++, "Tipo", tipo);
        Pantalla.campo(form, fila++, "Fecha límite", limite);
        Pantalla.campo(form, fila++, "Monto ($)", monto);
        Pantalla.campo(form, fila++, "Concepto", concepto);
        Pantalla.campo(form, fila++, "Categoría", categoria);
        Pantalla.campo(form, fila++, "", contraparte);
        // la etiqueta cambia segun el tipo, por eso se reemplaza la que puso el formulario
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.gridx = 0;
        c.gridy = fila - 1;
        c.anchor = java.awt.GridBagConstraints.EAST;
        c.insets = new java.awt.Insets(3, 3, 3, 6);
        form.remove(form.getComponentCount() - 2);
        form.add(etiquetaContraparte, c, form.getComponentCount() - 1);
        JButton guardar = new JButton("Guardar");
        guardar.addActionListener(e -> guardar());
        c = new java.awt.GridBagConstraints();
        c.gridx = 0;
        c.gridy = fila;
        c.gridwidth = 2;
        c.insets = new java.awt.Insets(10, 3, 3, 3);
        form.add(Pantalla.botones(guardar), c);
        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.add(form, BorderLayout.NORTH);
        return contenedor;
    }

    private JPanel armarFiltro() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        p.setBorder(new TitledBorder("¿Quién ya pagó y quién no?"));
        p.add(new JLabel("Categoría"));
        p.add(filtroCategoria);
        JButton ver = new JButton("Ver");
        ver.addActionListener(e -> buscar());
        p.add(ver);
        return p;
    }

    private TipoMovimiento movimientoDelTipo() {
        return ((TipoPendiente) tipo.getSelectedItem()).movimiento();
    }

    private void alCambiarTipo() {
        boolean cobrar = tipo.getSelectedItem() == TipoPendiente.POR_COBRAR;
        etiquetaContraparte.setText(cobrar ? "Usuario atendido" : "Proveedor");
        categoria.setModel(new javax.swing.DefaultComboBoxModel<>(
                Pantalla.comoArreglo(movimientos.categorias(movimientoDelTipo()))));
    }

    private void recargarFiltro() {
        List<String> todas = new ArrayList<>();
        todas.add(TODAS);
        todas.addAll(movimientos.categorias(TipoMovimiento.INGRESO));
        for (String c : movimientos.categorias(TipoMovimiento.EGRESO)) {
            if (!todas.contains(c)) {
                todas.add(c);
            }
        }
        filtroCategoria.setModel(new javax.swing.DefaultComboBoxModel<>(Pantalla.comoArreglo(todas)));
    }

    private void guardar() {
        try {
            servicio.registrar(new Pendiente(0, (TipoPendiente) tipo.getSelectedItem(),
                    Formatos.parseFecha(limite.getText()), Formatos.parseMonto(monto.getText()), concepto.getText(),
                    (String) categoria.getSelectedItem(), contraparte.getText(), false, null));
            monto.setText("");
            concepto.setText("");
            contraparte.setText("");
            buscar();
            alCambiar.run();
        } catch (RuntimeException e) {
            Pantalla.error(this, e);
        }
    }

    private void buscar() {
        try {
            String elegida = (String) filtroCategoria.getSelectedItem();
            mostrados = elegida == null || TODAS.equals(elegida) ? servicio.todos() : servicio.porCategoria(elegida);
            modelo.setRowCount(0);
            for (Pendiente p : mostrados) {
                String estado = p.pagado() ? "Pagado" : servicio.estaVencido(p) ? "VENCIDO" : "Pendiente";
                modelo.addRow(new Object[] { Formatos.fecha(p.fechaLimite()), p.tipo(), p.contraparte(), p.concepto(),
                        p.categoria(), Formatos.moneda(p.monto()), estado });
            }
        } catch (RuntimeException e) {
            Pantalla.error(this, e);
        }
    }

    private void marcarPagado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            Pantalla.info(this, "Primero elige un renglón de la tabla.");
            return;
        }
        Pendiente p = mostrados.get(tabla.convertRowIndexToModel(fila));
        if (p.pagado()) {
            Pantalla.info(this, "Este pendiente ya estaba pagado.");
            return;
        }
        FormaPago forma = null;
        if (p.tipo() == TipoPendiente.POR_COBRAR) {
            forma = (FormaPago) javax.swing.JOptionPane.showInputDialog(this, "¿Cómo se pagó?", "Forma de pago",
                    javax.swing.JOptionPane.QUESTION_MESSAGE, null, FormaPago.values(), FormaPago.EFECTIVO);
            if (forma == null) {
                return;
            }
        }
        if (!Pantalla.confirmar(this, "Se registrará " + (p.tipo() == TipoPendiente.POR_COBRAR ? "el ingreso" : "el egreso")
                + " de " + Formatos.moneda(p.monto()) + " con fecha de hoy. ¿Continuar?")) {
            return;
        }
        try {
            servicio.marcarPagado(p.id(), LocalDate.now(), forma);
            buscar();
            alCambiar.run();
        } catch (RuntimeException e) {
            Pantalla.error(this, e);
        }
    }

    /** Se llama cuando cambian los datos desde otra pestana. */
    void actualizar() {
        buscar();
    }
}
