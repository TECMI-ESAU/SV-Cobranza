package mx.tecmilenio.svcobranza.service;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.db.Database;
import mx.tecmilenio.svcobranza.db.PendienteDao;
import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Movimiento;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;
import mx.tecmilenio.svcobranza.model.TipoPendiente;
import mx.tecmilenio.svcobranza.ui.Formatos;

/** Pagos pendientes y alertas (RF-PEN-01 a RF-PEN-04). */
public class PendienteService {

    /** Dias de anticipacion con los que se avisa de un cobro o pago por vencer. */
    public static final int DIAS_AVISO = 7;

    private final Database db;
    private final PendienteDao pendientes;
    private final MovimientoService movimientos;
    private final BitacoraService bitacora;
    private final Clock reloj;

    public PendienteService(Database db, PendienteDao pendientes, MovimientoService movimientos,
            BitacoraService bitacora, Clock reloj) {
        this.db = db;
        this.pendientes = pendientes;
        this.movimientos = movimientos;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    public Pendiente registrar(Pendiente nuevo) {
        Pendiente p = new Pendiente(0, nuevo.tipo(), nuevo.fechaLimite(),
                nuevo.monto() == null ? null : nuevo.monto().setScale(2, RoundingMode.HALF_UP),
                nuevo.concepto() == null ? null : nuevo.concepto().trim(), nuevo.categoria(),
                nuevo.contraparte() == null ? null : nuevo.contraparte().trim(), false, null);
        validar(p);
        return db.transaccion(() -> {
            long id = pendientes.insertar(p);
            bitacora.registrar("PENDIENTE", "ALTA", p.monto(), descripcion(p));
            return new Pendiente(id, p.tipo(), p.fechaLimite(), p.monto(), p.concepto(), p.categoria(),
                    p.contraparte(), false, null);
        });
    }

    /** Marca el pendiente como pagado y genera el ingreso o egreso correspondiente. */
    public Movimiento marcarPagado(long id, LocalDate fechaPago, FormaPago formaPago) {
        Pendiente p = pendientes.buscarPorId(id)
                .orElseThrow(() -> new AppException("El pendiente ya no existe."));
        if (p.pagado()) {
            throw new AppException("El pendiente ya estaba pagado.");
        }
        if (fechaPago == null) {
            throw new AppException("Falta la fecha de pago.");
        }
        if (p.tipo().movimiento() == TipoMovimiento.INGRESO && formaPago == null) {
            throw new AppException("Falta la forma de pago.");
        }
        return db.transaccion(() -> {
            Movimiento nuevo = p.tipo().movimiento() == TipoMovimiento.INGRESO
                    ? new Movimiento(0, TipoMovimiento.INGRESO, fechaPago, p.monto(), p.concepto(),
                            p.categoria(), p.contraparte(), formaPago, null, false)
                    : new Movimiento(0, TipoMovimiento.EGRESO, fechaPago, p.monto(), p.concepto(),
                            p.categoria(), null, null, p.contraparte(), false);
            Movimiento guardado = movimientos.registrarSinRevisarDuplicado(nuevo);
            pendientes.actualizar(p.comoPagado(guardado.id()));
            bitacora.registrar("PENDIENTE", "PAGO", p.monto(), descripcion(p));
            return guardado;
        });
    }

    /** RF-PEN-03: cobros y pagos vencidos o que vencen dentro de los proximos dias. */
    public List<Pendiente> alertas() {
        return pendientes.sinPagarHasta(LocalDate.now(reloj).plusDays(DIAS_AVISO));
    }

    public boolean estaVencido(Pendiente p) {
        return p.vencido(LocalDate.now(reloj));
    }

    /** RF-PEN-04: quien ya pago y quien no dentro de una categoria. */
    public List<Pendiente> porCategoria(String categoria) {
        return pendientes.porCategoria(categoria);
    }

    public List<Pendiente> todos() {
        return pendientes.listarTodos();
    }

    private void validar(Pendiente p) {
        List<String> errores = new ArrayList<>();
        if (p.tipo() == null) {
            errores.add("Falta indicar si es por cobrar o por pagar.");
        }
        if (p.fechaLimite() == null) {
            errores.add("Falta la fecha límite.");
        }
        if (p.monto() == null || p.monto().signum() <= 0) {
            errores.add("El monto debe ser mayor que cero.");
        }
        if (p.concepto() == null || p.concepto().isEmpty()) {
            errores.add("Falta el concepto.");
        }
        if (p.contraparte() == null || p.contraparte().isEmpty()) {
            errores.add(p.tipo() == TipoPendiente.POR_PAGAR ? "Falta el proveedor." : "Falta el usuario atendido.");
        }
        if (p.tipo() != null && (p.categoria() == null
                || !movimientos.categorias(p.tipo().movimiento()).contains(p.categoria()))) {
            errores.add("Elige una categoría de la lista.");
        }
        if (!errores.isEmpty()) {
            throw new AppException(String.join("\n", errores));
        }
    }

    private static String descripcion(Pendiente p) {
        return p.tipo() + " " + Formatos.moneda(p.monto()) + " - " + p.concepto() + " ("
                + p.contraparte() + ") vence " + Formatos.fecha(p.fechaLimite());
    }
}
