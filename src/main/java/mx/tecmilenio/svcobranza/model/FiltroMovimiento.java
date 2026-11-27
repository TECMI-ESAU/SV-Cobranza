package mx.tecmilenio.svcobranza.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Criterios de busqueda de movimientos. Cualquier campo en null no se aplica.
 * "persona" busca en el nombre del usuario atendido.
 */
public record FiltroMovimiento(TipoMovimiento tipo, String persona, String concepto,
        LocalDate desde, LocalDate hasta, BigDecimal montoMin, BigDecimal montoMax,
        boolean incluirCancelados) {

    public static FiltroMovimiento de(TipoMovimiento tipo) {
        return new FiltroMovimiento(tipo, null, null, null, null, null, null, false);
    }
}
