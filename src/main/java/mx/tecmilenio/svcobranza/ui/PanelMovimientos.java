package mx.tecmilenio.svcobranza.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
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

import mx.tecmilenio.svcobranza.model.FiltroMovimiento;
import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Movimiento;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;
import mx.tecmilenio.svcobranza.service.MovimientoService;
import mx.tecmilenio.svcobranza.service.PosibleDuplicadoException;

/**
 * Pantalla de ingresos o egresos (RF-ING-01 a 04, RF-EGR-01 a 04, RF-TRA-01). Es la misma
 * pantalla para los dos tipos; cambian los campos del formulario.
 */
class PanelMovimientos extends JPanel {

    private static final long serialVersionUID = 1L;

    private final TipoMovimiento tipo;
    private final MovimientoService servicio;
    private final Runnable alCambiar;

    private final JTextField fecha = new JTextField(Formatos.fecha(LocalDate.now()));
    private final JTextField monto = new JTextField();
    private final JTextField concepto = new JTextField();
    private final JComboBox<String> categoria = new JComboBox<>();
    private final JTextField persona = new JTextField();
    private final JComboBox<FormaPago> formaPago = new JComboBox<>(FormaPago.values());

    private final JTextField filtroPersona = new JTextField(10);
    private final JTextField filtroConcepto = new JTextField(10);
    private final JTextField filtroDesde = new JTextField(8);
    private final JTextField filtroHasta = new JTextField(8);
    private final JTextField filtroMin = new JTextField(6);
    private final JTextField filtroMax = new JTextField(6);

    private final DefaultTableModel modelo;
    private final JTable tabla;
    private List<Movimiento> mostrados = List.of();
    private Movimiento enEdicion;
    private final JButton guardar = new JButton("Guardar");

    PanelMovimientos(TipoMovimiento tipo, MovimientoService servicio, Runnable alCambiar) {
        super(new BorderLayout());
        this.tipo = tipo;
        this.servicio = servicio;
        this.alCambiar = alCambiar;
        boolean esIngreso = tipo == TipoMovimiento.INGRESO;

        modelo = esIngreso
                ? Pantalla.modelo("Fecha", "Usuario atendido", "Concepto", "Categoría", "Forma de pago", "Monto")
                : Pantalla.modelo("Fecha", "Proveedor", "Concepto", "Categoría", "Monto");
        tabla = Pantalla.tabla(modelo);

        add(armarFormulario(esIngreso), BorderLayout.WEST);
        JPanel centro = new JPanel(new BorderLayout());
        centro.add(armarFiltros(esIngreso), BorderLayout.NORTH);
        centro.add(new JScrollPane(tabla), BorderLayout.CENTER);
        centro.add(armarAcciones(), BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);

        recargarCategorias();
        buscar();
    }

    private JPanel armarFormulario(boolean esIngreso) {
        JPanel form = Pantalla.formulario();
        form.setBorder(new TitledBorder(esIngreso ? "Registrar ingreso" : "Registrar egreso"));
        int fila = 0;
        Pantalla.campo(form, fila++, "Fecha (dd/mm/aaaa)", fecha);
        Pantalla.campo(form, fila++, "Monto ($)", monto);
        Pantalla.campo(form, fila++, "Concepto", concepto);
        Pantalla.campo(form, fila++, "Categoría", categoria);
        Pantalla.campo(form, fila++, esIngreso ? "Usuario atendido" : "Proveedor", persona);
        if (esIngreso) {
            Pantalla.campo(form, fila++, "Forma de pago", formaPago);
        }
        JButton limpiar = new JButton("Limpiar");
        guardar.addActionListener(e -> guardar());
        limpiar.addActionListener(e -> limpiarFormulario());
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.gridx = 0;
        c.gridy = fila;
        c.gridwidth = 2;
        c.insets = new java.awt.Insets(10, 3, 3, 3);
        form.add(Pantalla.botones(guardar, limpiar), c);
        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.add(form, BorderLayout.NORTH);
        return contenedor;
    }

    private JPanel armarFiltros(boolean esIngreso) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        p.setBorder(new TitledBorder("Buscar"));
        p.add(new JLabel(esIngreso ? "Usuario" : "Proveedor"));
        p.add(filtroPersona);
        p.add(new JLabel("Concepto"));
        p.add(filtroConcepto);
        p.add(new JLabel("Desde"));
        p.add(filtroDesde);
        p.add(new JLabel("Hasta"));
        p.add(filtroHasta);
        p.add(new JLabel("Monto de"));
        p.add(filtroMin);
        p.add(new JLabel("a"));
        p.add(filtroMax);
        JButton buscar = new JButton("Buscar");
        JButton todos = new JButton("Ver todos");
        buscar.addActionListener(e -> buscar());
        todos.addActionListener(e -> {
            for (JTextField f : List.of(filtroPersona, filtroConcepto, filtroDesde, filtroHasta, filtroMin, filtroMax)) {
                f.setText("");
            }
            buscar();
        });
        p.add(buscar);
        p.add(todos);
        return p;
    }

    private JPanel armarAcciones() {
        JButton editar = new JButton("Editar seleccionado");
        JButton cancelar = new JButton("Cancelar seleccionado");
        editar.addActionListener(e -> cargarParaEditar());
        cancelar.addActionListener(e -> cancelarSeleccionado());
        return Pantalla.botones(editar, cancelar);
    }

    /** Vuelve a leer las categorias, por si el servicio las cambio. */
    private void recargarCategorias() {
        categoria.setModel(new javax.swing.DefaultComboBoxModel<>(
                Pantalla.comoArreglo(servicio.categorias(tipo))));
    }

    private Movimiento leerFormulario(long id) {
        LocalDate f = Formatos.parseFecha(fecha.getText());
        BigDecimal m = Formatos.parseMonto(monto.getText());
        boolean esIngreso = tipo == TipoMovimiento.INGRESO;
        return new Movimiento(id, tipo, f, m, concepto.getText(), (String) categoria.getSelectedItem(),
                esIngreso ? persona.getText() : null, esIngreso ? (FormaPago) formaPago.getSelectedItem() : null,
                esIngreso ? null : persona.getText(), false);
    }

    private void guardar() {
        try {
            if (enEdicion != null) {
                servicio.editar(leerFormulario(enEdicion.id()));
                Pantalla.info(this, "Los cambios se guardaron.");
            } else {
                registrarConfirmandoDuplicado(leerFormulario(0));
            }
            limpiarFormulario();
            buscar();
            alCambiar.run();
        } catch (CancelacionDelUsuario cancelado) {
            // el usuario decidio no guardar el posible duplicado
        } catch (RuntimeException e) {
            Pantalla.error(this, e);
        }
    }

    private void registrarConfirmandoDuplicado(Movimiento nuevo) {
        try {
            servicio.registrar(nuevo, false);
        } catch (PosibleDuplicadoException e) {
            StringBuilder sb = new StringBuilder("Ya hay un movimiento con la misma fecha y monto:\n\n");
            for (Movimiento m : e.parecidos()) {
                sb.append("  • ").append(Formatos.fecha(m.fecha())).append("  ").append(Formatos.moneda(m.monto()))
                        .append("  ").append(m.concepto()).append(" ").append(m.persona().isEmpty() ? "" : "(" + m.persona() + ")")
                        .append('\n');
            }
            sb.append("\n¿Es un movimiento distinto y quieres guardarlo de todos modos?");
            if (!Pantalla.confirmar(this, sb.toString())) {
                throw new CancelacionDelUsuario();
            }
            servicio.registrar(nuevo, true);
        }
    }
    /** Se llama cuando cambian los datos desde otra pestana. */
    void actualizar() {
        buscar();
    }



    private void buscar() {
        try {
            mostrados = servicio.buscar(leerFiltro());
            modelo.setRowCount(0);
            for (Movimiento m : mostrados) {
                if (tipo == TipoMovimiento.INGRESO) {
                    modelo.addRow(new Object[] { Formatos.fecha(m.fecha()), m.persona(), m.concepto(), m.categoria(),
                            m.formaPago(), Formatos.moneda(m.monto()) });
                } else {
                    modelo.addRow(new Object[] { Formatos.fecha(m.fecha()), m.persona(), m.concepto(), m.categoria(),
                            Formatos.moneda(m.monto()) });
                }
            }
        } catch (RuntimeException e) {
            Pantalla.error(this, e);
        }
    }

    private FiltroMovimiento leerFiltro() {
        return new FiltroMovimiento(tipo, filtroPersona.getText(), filtroConcepto.getText(),
                vacio(filtroDesde) ? null : Formatos.parseFecha(filtroDesde.getText()),
                vacio(filtroHasta) ? null : Formatos.parseFecha(filtroHasta.getText()),
                vacio(filtroMin) ? null : Formatos.parseMonto(filtroMin.getText()),
                vacio(filtroMax) ? null : Formatos.parseMonto(filtroMax.getText()), false);
    }

    private Movimiento seleccionado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            Pantalla.info(this, "Primero elige un renglón de la tabla.");
            return null;
        }
        return mostrados.get(tabla.convertRowIndexToModel(fila));
    }

    private void cargarParaEditar() {
        Movimiento m = seleccionado();
        if (m == null) {
            return;
        }
        enEdicion = m;
        fecha.setText(Formatos.fecha(m.fecha()));
        monto.setText(m.monto().toPlainString());
        concepto.setText(m.concepto());
        categoria.setSelectedItem(m.categoria());
        persona.setText(m.persona());
        if (m.formaPago() != null) {
            formaPago.setSelectedItem(m.formaPago());
        }
        guardar.setText("Guardar cambios");
    }

    private void cancelarSeleccionado() {
        Movimiento m = seleccionado();
        if (m == null) {
            return;
        }
        if (!Pantalla.confirmar(this, "¿Cancelar este movimiento?\n" + Formatos.fecha(m.fecha()) + "  "
                + Formatos.moneda(m.monto()) + "  " + m.concepto()
                + "\n\nDejará de contarse en los balances, pero queda en la bitácora.")) {
            return;
        }
        try {
            servicio.cancelar(m.id());
            limpiarFormulario();
            buscar();
            alCambiar.run();
        } catch (RuntimeException e) {
            Pantalla.error(this, e);
        }
    }

    private void limpiarFormulario() {
        enEdicion = null;
        guardar.setText("Guardar");
        fecha.setText(Formatos.fecha(LocalDate.now()));
        monto.setText("");
        concepto.setText("");
        persona.setText("");
        categoria.setSelectedIndex(0);
        formaPago.setSelectedIndex(0);
    }

    private static boolean vacio(JTextField campo) {
        return campo.getText() == null || campo.getText().isBlank();
    }

    /** Se usa solo para salir del guardado cuando el usuario no confirma un duplicado. */
    private static final class CancelacionDelUsuario extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CancelacionDelUsuario() {
            super(null, null, false, false);
        }
    }
}
