package mx.tecmilenio.svcobranza.ui;

import java.math.BigDecimal;
import java.time.LocalDate;

import mx.tecmilenio.svcobranza.AppException;

/**
 * Lectura de lo que se escribe en los formularios. No usa JavaFX, por lo que se prueba en el CI
 * sin pantalla. Un campo vacio es "sin dato" (null); un valor mal escrito lanza un error en
 * español que nombra el campo.
 */
public final class Entradas {

    private Entradas() {
    }

    /** Fecha dd/mm/aaaa, o null si el texto esta en blanco. */
    public static LocalDate fecha(String texto, String campo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Formatos.parseFecha(texto);
        } catch (IllegalArgumentException e) {
            throw new AppException("La fecha de " + campo
                    + " debe tener el formato dd/mm/aaaa (por ejemplo 26/11/2026).");
        }
    }

    /** Monto en pesos (acepta "$" y comas de miles), o null si el texto esta en blanco. */
    public static BigDecimal monto(String texto, String campo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Formatos.parseMonto(texto);
        } catch (IllegalArgumentException e) {
            throw new AppException("El monto de " + campo + " debe ser un número, por ejemplo 1500.50.");
        }
    }
}
