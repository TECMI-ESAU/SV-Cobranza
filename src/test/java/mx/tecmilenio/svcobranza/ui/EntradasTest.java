package mx.tecmilenio.svcobranza.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import mx.tecmilenio.svcobranza.AppException;

class EntradasTest {

    @Test
    void leeFechasEnFormatoDiaMesAnio() {
        assertEquals(LocalDate.of(2026, 11, 26), Entradas.fecha("26/11/2026", "ingreso"));
        assertEquals(LocalDate.of(2026, 1, 5), Entradas.fecha("  05/01/2026 ", "ingreso"));
    }

    @Test
    void unaFechaEnBlancoEsSinDato() {
        assertNull(Entradas.fecha(null, "ingreso"));
        assertNull(Entradas.fecha("", "ingreso"));
        assertNull(Entradas.fecha("   ", "ingreso"));
    }

    @Test
    void unaFechaMalEscritaNombraElCampoYElFormato() {
        for (String mala : new String[] { "2026-11-26", "26/13/2026", "31/02/2026", "ayer", "26/11", "26-11-2026" }) {
            AppException e = assertThrows(AppException.class, () -> Entradas.fecha(mala, "cobro semanal"),
                    "debía rechazar: " + mala);
            assertTrue(e.getMessage().contains("cobro semanal"));
            assertTrue(e.getMessage().contains("dd/mm/aaaa"));
        }
    }

    @Test
    void leeMontosConSignoDePesosYComasDeMiles() {
        assertEquals(new BigDecimal("1500.50"), Entradas.monto("1500.50", "estancia"));
        assertEquals(new BigDecimal("3500"), Entradas.monto("$3,500", "estancia"));
        assertEquals(new BigDecimal("12500.00"), Entradas.monto(" $ 12,500.00 ", "estancia"));
    }

    @Test
    void unMontoEnBlancoEsSinDato() {
        assertNull(Entradas.monto(null, "tiendita"));
        assertNull(Entradas.monto("", "tiendita"));
        assertNull(Entradas.monto("  ", "tiendita"));
    }

    @Test
    void unMontoMalEscritoNombraElCampo() {
        for (String malo : new String[] { "mucho", "12abc", "1.2.3", "$" }) {
            AppException e = assertThrows(AppException.class, () -> Entradas.monto(malo, "experiencia"),
                    "debía rechazar: " + malo);
            assertTrue(e.getMessage().contains("experiencia"));
            assertTrue(e.getMessage().contains("número"));
        }
    }
}
