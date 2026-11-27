package mx.tecmilenio.svcobranza.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cobro a un usuario atendido. "categoria" es el concepto (Pago semanal, Pago mensual, Médico,
 * Psicólogo o Experiencia). Un id de 0 indica que aun no se guarda; movimientoId es null
 * mientras no se pague. "motivo" es opcional y solo se usa al condonar.
 */
public record Pendiente(long id, long usuarioId, String usuario, String categoria, LocalDate fechaLimite,
        BigDecimal monto, EstadoPendiente estado, String motivo, Long movimientoId) {

    public Pendiente comoPagado(long idMovimiento) {
        return new Pendiente(id, usuarioId, usuario, categoria, fechaLimite, monto, EstadoPendiente.PAGADO,
                motivo, idMovimiento);
    }

    public Pendiente comoCondonado(String motivoCondonacion) {
        return new Pendiente(id, usuarioId, usuario, categoria, fechaLimite, monto, EstadoPendiente.CONDONADO,
                motivoCondonacion, null);
    }

    public Pendiente comoPendiente() {
        return new Pendiente(id, usuarioId, usuario, categoria, fechaLimite, monto, EstadoPendiente.PENDIENTE,
                null, null);
    }

    public boolean vencido(LocalDate hoy) {
        return estado == EstadoPendiente.PENDIENTE && fechaLimite.isBefore(hoy);
    }
}
