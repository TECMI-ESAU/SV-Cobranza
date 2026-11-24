package mx.tecmilenio.svcobranza.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Totales de ingresos y egresos de un periodo, con el detalle por categoria. */
public record Balance(LocalDate desde, LocalDate hasta,
        Map<String, BigDecimal> ingresosPorCategoria,
        Map<String, BigDecimal> egresosPorCategoria) {

    public BigDecimal totalIngresos() {
        return suma(ingresosPorCategoria);
    }

    public BigDecimal totalEgresos() {
        return suma(egresosPorCategoria);
    }

    public BigDecimal saldo() {
        return totalIngresos().subtract(totalEgresos());
    }

    /** RF-BAL-02: indica cuando los egresos superan a los ingresos del periodo. */
    public boolean egresosSuperanIngresos() {
        return totalEgresos().compareTo(totalIngresos()) > 0;
    }

    /** Porcentaje que pesa cada categoria dentro del total de ingresos. */
    public Map<String, BigDecimal> porcentajesIngresos() {
        return porcentajes(ingresosPorCategoria);
    }

    /** Porcentaje que pesa cada categoria dentro del total de egresos. */
    public Map<String, BigDecimal> porcentajesEgresos() {
        return porcentajes(egresosPorCategoria);
    }

    private static BigDecimal suma(Map<String, BigDecimal> valores) {
        return valores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<String, BigDecimal> porcentajes(Map<String, BigDecimal> valores) {
        BigDecimal total = suma(valores);
        Map<String, BigDecimal> resultado = new LinkedHashMap<>();
        if (total.signum() == 0) {
            return resultado;
        }
        for (Map.Entry<String, BigDecimal> e : valores.entrySet()) {
            resultado.put(e.getKey(), e.getValue().multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, RoundingMode.HALF_UP));
        }
        return resultado;
    }
}
