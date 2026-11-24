package mx.tecmilenio.svcobranza.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.db.MovimientoDao;
import mx.tecmilenio.svcobranza.db.PendienteDao;
import mx.tecmilenio.svcobranza.model.Balance;
import mx.tecmilenio.svcobranza.model.ReporteUsuario;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;

/** Balance y reportes (RF-BAL-01 a RF-BAL-04). */
public class BalanceService {

    private final MovimientoDao movimientos;
    private final PendienteDao pendientes;
    private final Clock reloj;

    public BalanceService(MovimientoDao movimientos, PendienteDao pendientes, Clock reloj) {
        this.movimientos = movimientos;
        this.pendientes = pendientes;
        this.reloj = reloj;
    }

    /** RF-BAL-01: totales y saldo entre dos fechas, ambas incluidas. */
    public Balance calcular(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new AppException("Indica la fecha inicial y la final.");
        }
        if (hasta.isBefore(desde)) {
            throw new AppException("La fecha final no puede ser anterior a la inicial.");
        }
        return new Balance(desde, hasta,
                movimientos.totalesPorCategoria(TipoMovimiento.INGRESO, desde, hasta),
                movimientos.totalesPorCategoria(TipoMovimiento.EGRESO, desde, hasta));
    }

    /** Balance de la semana en curso, de lunes a domingo. */
    public Balance semanaActual() {
        LocalDate hoy = LocalDate.now(reloj);
        return calcular(hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
    }

    /** RF-BAL-04: todos los pagos de un usuario atendido y lo que aun adeuda. */
    public ReporteUsuario reportePorUsuario(String usuario) {
        if (usuario == null || usuario.isBlank()) {
            throw new AppException("Indica el nombre del usuario atendido.");
        }
        return new ReporteUsuario(usuario.trim(), movimientos.listarPorUsuario(usuario),
                pendientes.porContraparte(usuario));
    }
}
