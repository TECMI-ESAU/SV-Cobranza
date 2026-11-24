package mx.tecmilenio.svcobranza.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

import mx.tecmilenio.svcobranza.model.Balance;
import mx.tecmilenio.svcobranza.model.ReporteUsuario;
import mx.tecmilenio.svcobranza.service.BalanceService;
import mx.tecmilenio.svcobranza.service.ExcelService;

/** Balance por periodo (RF-BAL-01..03), reporte por usuario (RF-BAL-04) y exportacion a Excel (RF-BAL-05). */
class PanelBalance extends JPanel {

    private static final long serialVersionUID = 1L;

    private final BalanceService servicio;
    private final ExcelService excel;
    private final JTextField desde = new JTextField();
    private final JTextField hasta = new JTextField();
    private final JTextField usuario = new JTextField();
    private final JLabel ingresos = new JLabel();
    private final JLabel egresos = new JLabel();
    private final JLabel saldo = new JLabel();
    private final JLabel aviso = new JLabel(" ");
    private final DefaultTableModel modelo = Pantalla.modelo("Tipo", "Categoría", "Monto", "Porcentaje");
    private Balance actual;
    private ReporteUsuario reporte;

    PanelBalance(BalanceService servicio, ExcelService excel) {
        super(new BorderLayout());
        this.servicio = servicio;
        this.excel = excel;

        JPanel form = Pantalla.formulario();
        Pantalla.campo(form, 0, "Desde (dd/mm/aaaa):", desde);
        Pantalla.campo(form, 1, "Hasta (dd/mm/aaaa):", hasta);
        Pantalla.campo(form, 2, "Ingresos:", ingresos);
        Pantalla.campo(form, 3, "Egresos:", egresos);
        Pantalla.campo(form, 4, "Saldo:", saldo);
        aviso.setForeground(new Color(0xB00020));
        Pantalla.campo(form, 5, "", aviso);

        JButton calcular = new JButton("Calcular");
        calcular.addActionListener(e -> calcular());
        JButton semana = new JButton("Semana actual");
        semana.addActionListener(e -> semanaActual());
        JButton exportar = new JButton("Exportar balance a Excel...");
        exportar.addActionListener(e -> exportarBalance());

        JPanel arriba = new JPanel(new BorderLayout());
        arriba.add(form, BorderLayout.NORTH);
        arriba.add(Pantalla.botones(calcular, semana, exportar), BorderLayout.CENTER);

        JPanel porUsuario = Pantalla.formulario();
        Pantalla.campo(porUsuario, 0, "Usuario atendido:", usuario);
        JButton generar = new JButton("Exportar reporte del usuario a Excel...");
        generar.addActionListener(e -> exportarReporte());
        JPanel abajo = new JPanel(new BorderLayout());
        abajo.add(porUsuario, BorderLayout.CENTER);
        abajo.add(Pantalla.botones(generar), BorderLayout.SOUTH);

        add(Pantalla.conTitulo("Balance", arriba), BorderLayout.NORTH);
        add(Pantalla.desplazable(Pantalla.tabla(modelo)), BorderLayout.CENTER);
        add(abajo, BorderLayout.SOUTH);
        semanaActual();
    }

    private void semanaActual() {
        try {
            mostrar(servicio.semanaActual());
            desde.setText(Formatos.fecha(actual.desde()));
            hasta.setText(Formatos.fecha(actual.hasta()));
        } catch (RuntimeException e) {
            Pantalla.error(this, e);
        }
    }

    private void calcular() {
        try {
            mostrar(servicio.calcular(Formatos.parseFecha(desde.getText()), Formatos.parseFecha(hasta.getText())));
        } catch (RuntimeException e) {
            Pantalla.error(this, e);
        }
    }

    /** Se llama cuando cambian los datos desde otra pestana. */
    void actualizar() {
        if (actual != null) {
            mostrar(servicio.calcular(actual.desde(), actual.hasta()));
        }
    }

    private void mostrar(Balance b) {
        actual = b;
        ingresos.setText(Formatos.moneda(b.totalIngresos()));
        egresos.setText(Formatos.moneda(b.totalEgresos()));
        saldo.setText(Formatos.moneda(b.saldo()));
        aviso.setText(b.egresosSuperanIngresos() ? "Atención: los egresos superan a los ingresos del periodo." : " ");
        modelo.setRowCount(0);
        agregar("Ingreso", b.ingresosPorCategoria(), b.porcentajesIngresos());
        agregar("Egreso", b.egresosPorCategoria(), b.porcentajesEgresos());
    }

    private void agregar(String tipo, Map<String, BigDecimal> montos, Map<String, BigDecimal> porcentajes) {
        montos.forEach((categoria, monto) -> modelo.addRow(new Object[] { tipo, categoria,
                Formatos.moneda(monto), porcentajes.getOrDefault(categoria, BigDecimal.ZERO) + " %" }));
    }

    private void exportarBalance() {
        if (actual == null) {
            Pantalla.info(this, "Primero calcula el balance.");
            return;
        }
        Path destino = elegirArchivo("balance-" + LocalDate.now() + ".xlsx");
        if (destino == null) {
            return;
        }
        try {
            excel.exportarBalance(actual, destino);
            Pantalla.info(this, "Archivo guardado en:\n" + destino);
        } catch (RuntimeException e) {
            Pantalla.error(this, e);
        }
    }

    private void exportarReporte() {
        try {
            reporte = servicio.reportePorUsuario(usuario.getText());
            Path destino = elegirArchivo("reporte-" + reporte.usuario().replaceAll("[^\\p{L}\\p{N}]+", "_") + ".xlsx");
            if (destino == null) {
                return;
            }
            excel.exportarReporteUsuario(reporte, destino);
            Pantalla.info(this, "Archivo guardado en:\n" + destino);
        } catch (RuntimeException e) {
            Pantalla.error(this, e);
        }
    }

    private Path elegirArchivo(String sugerido) {
        JFileChooser s = new JFileChooser();
        s.setFileFilter(new FileNameExtensionFilter("Excel (*.xlsx)", "xlsx"));
        s.setSelectedFile(new File(sugerido));
        if (s.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File f = s.getSelectedFile();
        return f.getName().toLowerCase().endsWith(".xlsx")
                ? f.toPath()
                : new File(f.getParentFile(), f.getName() + ".xlsx").toPath();
    }
}
