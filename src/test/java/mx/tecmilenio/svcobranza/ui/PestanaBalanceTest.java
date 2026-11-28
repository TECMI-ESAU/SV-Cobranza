package mx.tecmilenio.svcobranza.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import mx.tecmilenio.svcobranza.model.Balance;

class PestanaBalanceTest {

    @Test
    void elDetalleListaPrimeroLosIngresosYLuegoLosEgresosConSuPorcentaje() {
        Map<String, BigDecimal> ingresos = new LinkedHashMap<>();
        ingresos.put("Pago mensual", new BigDecimal("3000.00"));
        ingresos.put("Tiendita", new BigDecimal("1000.00"));
        Map<String, BigDecimal> egresos = new LinkedHashMap<>();
        egresos.put("Renta", new BigDecimal("500.00"));
        Balance balance = new Balance(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), ingresos, egresos);

        List<PestanaBalance.Fila> filas = PestanaBalance.filas(balance);

        assertEquals(3, filas.size());
        assertEquals("Ingreso", filas.get(0).tipo());
        assertEquals("Pago mensual", filas.get(0).categoria());
        assertEquals("75.00 %", filas.get(0).porcentaje());
        assertEquals("Egreso", filas.get(2).tipo());
        assertEquals("100.00 %", filas.get(2).porcentaje());
        assertTrue(filas.get(2).monto().contains("500"));
    }

    @Test
    void sinMovimientosNoHayFilas() {
        Balance vacio = new Balance(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), Map.of(), Map.of());
        assertTrue(PestanaBalance.filas(vacio).isEmpty());
    }
}
