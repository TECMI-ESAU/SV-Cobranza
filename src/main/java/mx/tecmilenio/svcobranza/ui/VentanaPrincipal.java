package mx.tecmilenio.svcobranza.ui;

import java.util.List;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;

import mx.tecmilenio.svcobranza.Aplicacion;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;

/** Ventana unica de la aplicacion: una pestana por modulo. */
public class VentanaPrincipal extends JFrame {

    private static final long serialVersionUID = 1L;

    private final Aplicacion app;
    private final PanelMovimientos ingresos;
    private final PanelMovimientos egresos;
    private final PanelPendientes pendientes;
    private final PanelBalance balance;
    private final PanelRespaldo respaldo;
    private final PanelBitacora bitacora;

    public VentanaPrincipal(Aplicacion app) {
        super("SV-Cobranza - Soplando Vida A.C.");
        this.app = app;
        ingresos = new PanelMovimientos(TipoMovimiento.INGRESO, app.movimientos(), this::datosCambiaron);
        egresos = new PanelMovimientos(TipoMovimiento.EGRESO, app.movimientos(), this::datosCambiaron);
        pendientes = new PanelPendientes(app.pendientes(), app.movimientos(), this::datosCambiaron);
        balance = new PanelBalance(app.balance(), app.excel());
        respaldo = new PanelRespaldo(app.respaldo(), app.bitacora(), this::datosCambiaron);
        bitacora = new PanelBitacora(app.bitacora());

        JTabbedPane pestanas = new JTabbedPane();
        pestanas.addTab("Ingresos", ingresos);
        pestanas.addTab("Egresos", egresos);
        pestanas.addTab("Pendientes", pendientes);
        pestanas.addTab("Balance", balance);
        pestanas.addTab("Respaldo", respaldo);
        pestanas.addTab("Bitácora", bitacora);
        add(pestanas);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                app.close();
                dispose();
                System.exit(0);
            }
        });
        setSize(1100, 700);
        setLocationRelativeTo(null);
    }

    /** Refresca las pantallas que dependen de los datos tras un cambio. */
    private void datosCambiaron() {
        ingresos.actualizar();
        egresos.actualizar();
        pendientes.actualizar();
        balance.actualizar();
        respaldo.actualizar();
        bitacora.actualizar();
    }

    /** RF-PEN-03: al abrir, avisa de los pendientes vencidos o por vencer. No muestra nada si no hay. */
    public void mostrarAlertas() {
        List<Pendiente> alertas = app.pendientes().alertas();
        if (alertas.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder("Pendientes vencidos o que vencen en los próximos "
                + mx.tecmilenio.svcobranza.service.PendienteService.DIAS_AVISO + " días:\n\n");
        for (Pendiente p : alertas) {
            sb.append(app.pendientes().estaVencido(p) ? "VENCIDO  " : "Por vencer  ")
                    .append(Formatos.fecha(p.fechaLimite())).append("  ")
                    .append(p.tipo()).append("  ").append(Formatos.moneda(p.monto()))
                    .append("  ").append(p.concepto()).append('\n');
        }
        JOptionPane.showMessageDialog(this, sb.toString(), "Pendientes por atender", JOptionPane.WARNING_MESSAGE);
    }
}
