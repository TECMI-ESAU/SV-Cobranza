package mx.tecmilenio.svcobranza.model;

import java.math.BigDecimal;
import java.util.List;

/** Todos los pagos e ingresos de dinero de un usuario atendido, mas lo que adeuda. */
public record ReporteUsuario(String usuario, List<Movimiento> movimientos,
        List<Pendiente> pendientes) {

    public BigDecimal totalPagado() {
        return movimientos.stream().map(Movimiento::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalAdeudado() {
        return pendientes.stream().filter(p -> p.estado() == EstadoPendiente.PENDIENTE).map(Pendiente::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
