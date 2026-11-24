package mx.tecmilenio.svcobranza.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * Formatos de Mexico: moneda en pesos y fechas dd/MM/aaaa.
 */
public final class Formatos {

    private static final Locale MEXICO = new Locale("es", "MX");
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss");

    private Formatos() {
    }

    public static String moneda(BigDecimal monto) {
        if (monto == null) {
            return "";
        }
        return NumberFormat.getCurrencyInstance(MEXICO).format(monto);
    }

    public static String fecha(LocalDate fecha) {
        return fecha == null ? "" : FECHA.format(fecha);
    }

    public static String fechaHora(LocalDateTime fechaHora) {
        return fechaHora == null ? "" : FECHA_HORA.format(fechaHora);
    }

    /** Lee una fecha dd/MM/aaaa. */
    public static LocalDate parseFecha(String texto) {
        try {
            return LocalDate.parse(texto.trim(), FECHA);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("La fecha debe tener el formato dd/mm/aaaa (por ejemplo 26/11/2026).");
        }
    }

    /** Lee un monto, aceptando "$" y comas de miles. */
    public static BigDecimal parseMonto(String texto) {
        try {
            String limpio = texto.replaceAll("[$,\\s]", "");
            return new BigDecimal(limpio);
        } catch (NumberFormatException | NullPointerException e) {
            throw new IllegalArgumentException("El monto debe ser un número, por ejemplo 1500.50.");
        }
    }
}
