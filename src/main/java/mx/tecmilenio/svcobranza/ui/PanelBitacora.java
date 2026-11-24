package mx.tecmilenio.svcobranza.ui;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;

import mx.tecmilenio.svcobranza.model.EntradaBitacora;
import mx.tecmilenio.svcobranza.service.BitacoraService;

/** Consulta de la bitacora de operaciones (RF-BIT-02). Solo lectura. */
class PanelBitacora extends JPanel {

    private static final long serialVersionUID = 1L;

    private final BitacoraService servicio;
    private final DefaultTableModel modelo =
            Pantalla.modelo("Fecha y hora", "Tipo", "Acción", "Monto", "Detalle");

    PanelBitacora(BitacoraService servicio) {
        super(new BorderLayout());
        this.servicio = servicio;
        JButton actualizar = new JButton("Actualizar");
        actualizar.addActionListener(e -> actualizar());
        add(Pantalla.conTitulo("Bitácora de operaciones",
                Pantalla.desplazable(Pantalla.tabla(modelo))), BorderLayout.CENTER);
        add(Pantalla.botones(actualizar), BorderLayout.SOUTH);
        actualizar();
    }

    final void actualizar() {
        modelo.setRowCount(0);
        for (EntradaBitacora b : servicio.consultar()) {
            modelo.addRow(new Object[] { Formatos.fechaHora(b.fechaHora()), b.tipo(), b.accion(),
                    Formatos.moneda(b.monto()), b.detalle() });
        }
    }
}
