package mx.tecmilenio.svcobranza.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Renglon de la bitacora. "tipo" es INGRESO, EGRESO, PENDIENTE o RESPALDO; "accion" es
 * ALTA, EDICION, CANCELACION, PAGO, GENERACION o RESTAURACION.
 */
public record EntradaBitacora(long id, LocalDateTime fechaHora, String tipo, String accion,
        BigDecimal monto, String detalle) {
}
