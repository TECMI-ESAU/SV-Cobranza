package mx.tecmilenio.svcobranza.ui;

import java.time.LocalDate;

import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

/** Selector de fechas con el formato dd/mm/aaaa de Mexico. */
public final class Fechas {

    private static final StringConverter<LocalDate> CONVERTIDOR = new StringConverter<>() {
        @Override
        public String toString(LocalDate fecha) {
            return Formatos.fecha(fecha);
        }

        @Override
        public LocalDate fromString(String texto) {
            if (texto == null || texto.isBlank()) {
                return null;
            }
            try {
                return Formatos.parseFecha(texto);
            } catch (IllegalArgumentException e) {
                return null; // texto incompleto mientras se escribe: el selector conserva el valor anterior
            }
        }
    };

    private Fechas() {
    }

    /** Convertidor para usar en cualquier DatePicker. */
    public static StringConverter<LocalDate> convertidor() {
        return CONVERTIDOR;
    }

    /** Selector de fecha en dd/mm/aaaa, con la fecha inicial indicada (puede ser null). */
    public static DatePicker selector(LocalDate inicial) {
        DatePicker selector = new DatePicker(inicial);
        selector.setConverter(CONVERTIDOR);
        selector.setPromptText("dd/mm/aaaa");
        selector.setEditable(true);
        return selector;
    }
}
