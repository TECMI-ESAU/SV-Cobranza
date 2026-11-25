package mx.tecmilenio.svcobranza.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un ingreso o egreso. Para un ingreso se usa "usuario" (usuario atendido) y "formaPago";
 * para un egreso se usa "proveedor". Un id de 0 indica que aun no se guarda.
 */
public record Movimiento(long id, TipoMovimiento tipo, LocalDate fecha, BigDecimal monto,
        String concepto, String categoria, String usuario, FormaPago formaPago,
        String proveedor, boolean cancelado) {

    public Movimiento conId(long nuevoId) {
        return new Movimiento(nuevoId, tipo, fecha, monto, concepto, categoria, usuario,
                formaPago, proveedor, cancelado);
    }

    public Movimiento comoCancelado() {
        return new Movimiento(id, tipo, fecha, monto, concepto, categoria, usuario,
                formaPago, proveedor, true);
    }

    /** Usuario atendido (ingresos) o proveedor (egresos). */
    public String persona() {
        String valor = tipo == TipoMovimiento.INGRESO ? usuario : proveedor;
        return valor == null ? "" : valor;
    }
}
