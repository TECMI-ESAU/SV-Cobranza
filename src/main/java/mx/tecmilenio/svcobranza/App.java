package mx.tecmilenio.svcobranza;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import mx.tecmilenio.svcobranza.ui.VentanaPrincipal;

/** Punto de entrada. Los datos se guardan en la carpeta del usuario: ~/SV-Cobranza-datos. */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // se usa el aspecto por defecto
            }
            try {
                Path carpeta = Paths.get(System.getProperty("user.home"), "SV-Cobranza-datos");
                Aplicacion app = Aplicacion.enCarpeta(carpeta);
                VentanaPrincipal ventana = new VentanaPrincipal(app);
                ventana.setVisible(true);
                ventana.mostrarAlertas();
            } catch (AppException e) {
                JOptionPane.showMessageDialog(null, e.getMessage(), "SV-Cobranza", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
