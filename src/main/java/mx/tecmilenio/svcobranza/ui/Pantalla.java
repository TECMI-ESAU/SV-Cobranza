package mx.tecmilenio.svcobranza.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;

import mx.tecmilenio.svcobranza.AppException;

/** Utilerias para armar pantallas sencillas con formularios y tablas. */
final class Pantalla {

    private Pantalla() {
    }

    static JPanel formulario() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return p;
    }

    /** Agrega una etiqueta y un campo en la fila indicada del formulario. */
    static void campo(JPanel form, int fila, String etiqueta, JComponent componente) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = fila;
        c.insets = new Insets(3, 3, 3, 6);
        c.anchor = GridBagConstraints.EAST;
        c.gridx = 0;
        form.add(new JLabel(etiqueta), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        componente.setPreferredSize(new Dimension(260, componente.getPreferredSize().height));
        form.add(componente, c);
    }

    static JTable tabla(DefaultTableModel modelo) {
        JTable t = new JTable(modelo) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };
        t.setRowHeight(24);
        t.setAutoCreateRowSorter(true);
        t.getTableHeader().setReorderingAllowed(false);
        return t;
    }

    static DefaultTableModel modelo(String... columnas) {
        return new DefaultTableModel(columnas, 0);
    }

    static JScrollPane desplazable(JTable tabla) {
        return new JScrollPane(tabla);
    }

    static JPanel botones(JButton... botones) {
        JPanel p = new JPanel();
        for (JButton b : botones) {
            p.add(b);
        }
        return p;
    }

    static JPanel conTitulo(String titulo, Component contenido) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel l = new JLabel(titulo, SwingConstants.LEFT);
        l.setFont(l.getFont().deriveFont(java.awt.Font.BOLD, 15f));
        l.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        p.add(l, BorderLayout.NORTH);
        p.add(contenido, BorderLayout.CENTER);
        return p;
    }

    static void error(Component padre, Exception e) {
        String mensaje = e instanceof AppException || e instanceof IllegalArgumentException
                ? e.getMessage()
                : "Ocurrió un error inesperado: " + e;
        JOptionPane.showMessageDialog(padre, mensaje, "Revisa los datos", JOptionPane.WARNING_MESSAGE);
    }

    static void info(Component padre, String mensaje) {
        JOptionPane.showMessageDialog(padre, mensaje, "SV-Cobranza", JOptionPane.INFORMATION_MESSAGE);
    }

    static boolean confirmar(Component padre, String mensaje) {
        return JOptionPane.showConfirmDialog(padre, mensaje, "Confirma", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    static String[] comoArreglo(List<String> lista) {
        return lista.toArray(new String[0]);
    }
}
