package mx.tecmilenio.svcobranza.ui;

import java.awt.BorderLayout;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import mx.tecmilenio.svcobranza.model.EntradaBitacora;
import mx.tecmilenio.svcobranza.service.BitacoraService;
import mx.tecmilenio.svcobranza.service.RespaldoService;

/** Respaldo cifrado y restauracion (RF-RES-01, RF-RES-02) y fecha del ultimo respaldo (SEG-04). */
class PanelRespaldo extends JPanel {

    private static final long serialVersionUID = 1L;

    private final RespaldoService respaldo;
    private final BitacoraService bitacora;
    private final Runnable alCambiar;
    private final JLabel ultimo = new JLabel();

    PanelRespaldo(RespaldoService respaldo, BitacoraService bitacora, Runnable alCambiar) {
        super(new BorderLayout());
        this.respaldo = respaldo;
        this.bitacora = bitacora;
        this.alCambiar = alCambiar;

        JButton generar = new JButton("Generar respaldo...");
        generar.addActionListener(e -> generar());
        JButton restaurar = new JButton("Restaurar respaldo...");
        restaurar.addActionListener(e -> restaurar());

        JPanel form = Pantalla.formulario();
        Pantalla.campo(form, 0, "Último respaldo:", ultimo);
        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.add(form, BorderLayout.NORTH);
        contenedor.add(Pantalla.botones(generar, restaurar), BorderLayout.CENTER);
        add(Pantalla.conTitulo("Respaldo y restauración", contenedor), BorderLayout.NORTH);
        actualizar();
    }

    final void actualizar() {
        Optional<LocalDateTime> fecha = bitacora.consultar().stream()
                .filter(b -> b.tipo().equals("RESPALDO") && b.accion().equals("GENERACION"))
                .map(EntradaBitacora::fechaHora)
                .max(LocalDateTime::compareTo);
        ultimo.setText(fecha.map(Formatos::fechaHora).orElse("Todavía no se ha generado ninguno"));
    }

    private void generar() {
        JFileChooser selector = selector("respaldo-" + java.time.LocalDate.now() + ".svr");
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        char[] contrasena = pedirContrasena("Escribe una contraseña para proteger el respaldo.\n"
                + "La necesitarás para restaurarlo; no se puede recuperar.");
        if (contrasena == null) {
            return;
        }
        try {
            Path destino = conExtension(selector.getSelectedFile());
            respaldo.generar(destino, contrasena);
            Pantalla.info(this, "Respaldo guardado en:\n" + destino);
            actualizar();
            alCambiar.run();
        } catch (RuntimeException e) {
            Pantalla.error(this, e);
        } finally {
            Arrays.fill(contrasena, ' ');
        }
    }

    private void restaurar() {
        JFileChooser selector = selector("");
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        if (!Pantalla.confirmar(this,
                "Restaurar reemplaza TODOS los datos actuales por los del respaldo.\n¿Deseas continuar?")) {
            return;
        }
        char[] contrasena = pedirContrasena("Escribe la contraseña del respaldo.");
        if (contrasena == null) {
            return;
        }
        try {
            respaldo.restaurar(selector.getSelectedFile().toPath(), contrasena);
            Pantalla.info(this, "Respaldo restaurado correctamente.");
            actualizar();
            alCambiar.run();
        } catch (RuntimeException e) {
            Pantalla.error(this, e);
        } finally {
            Arrays.fill(contrasena, ' ');
        }
    }

    private static JFileChooser selector(String nombreSugerido) {
        JFileChooser s = new JFileChooser();
        s.setFileFilter(new FileNameExtensionFilter("Respaldo SV-Cobranza (*.svr)", "svr"));
        if (!nombreSugerido.isEmpty()) {
            s.setSelectedFile(new File(nombreSugerido));
        }
        return s;
    }

    private static Path conExtension(File archivo) {
        return archivo.getName().toLowerCase().endsWith(".svr")
                ? archivo.toPath()
                : new File(archivo.getParentFile(), archivo.getName() + ".svr").toPath();
    }

    /** Devuelve la contrasena o null si el usuario cancela. */
    private char[] pedirContrasena(String mensaje) {
        JPasswordField campo = new JPasswordField(20);
        int r = JOptionPane.showConfirmDialog(this, new Object[] { mensaje, campo }, "Contraseña del respaldo",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return r == JOptionPane.OK_OPTION ? campo.getPassword() : null;
    }
}
