package mx.tecmilenio.svcobranza.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un ingreso o egreso. "usuario" es el nombre del usuario atendido, guardado como texto para
 * conservar el historial aunque el usuario se borre: lo llevan los ingresos y los egresos de
 * "Donación a usuario". "formaPago" solo aplica a los ingresos. Un id de 0 indica que aun
 * no se guarda.
 */
public record Movimiento(long id, TipoMovimiento tipo, LocalDate fecha, BigDecimal monto,
        String concepto, String categoria, String usuario, FormaPago formaPago, boolean cancelado) {

    public Movimiento conId(long nuevoId) {
        return new Movimiento(nuevoId, tipo, fecha, monto, concepto, categoria, usuario, formaPago, cancelado);
    }

    public Movimiento comoCancelado() {
        return new Movimiento(id, tipo, fecha, monto, concepto, categoria, usuario, formaPago, true);
    }

    /** Nombre del usuario atendido, o vacio si no lleva. */
    public String persona() {
        return usuario == null ? "" : usuario;
    }
}
