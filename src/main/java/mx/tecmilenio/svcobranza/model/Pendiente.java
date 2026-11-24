package mx.tecmilenio.svcobranza.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pago pendiente por cobrar (a un usuario atendido) o por pagar (a un proveedor).
 * Un id de 0 indica que aun no se guarda; movimientoId es null mientras no se pague.
 */
public record Pendiente(long id, TipoPendiente tipo, LocalDate fechaLimite, BigDecimal monto,
        String concepto, String categoria, String contraparte, boolean pagado,
        Long movimientoId) {

    public Pendiente comoPagado(long idMovimiento) {
        return new Pendiente(id, tipo, fechaLimite, monto, concepto, categoria, contraparte,
                true, idMovimiento);
    }

    public boolean vencido(LocalDate hoy) {
        return !pagado && fechaLimite.isBefore(hoy);
    }
}
