package mx.tecmilenio.svcobranza.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.db.CategoriaDao;
import mx.tecmilenio.svcobranza.db.Database;
import mx.tecmilenio.svcobranza.db.MovimientoDao;
import mx.tecmilenio.svcobranza.model.FiltroMovimiento;
import mx.tecmilenio.svcobranza.model.Movimiento;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;
import mx.tecmilenio.svcobranza.ui.Formatos;

/**
 * Ingresos y egresos: alta, clasificacion, busqueda, edicion, cancelacion y deteccion
 * de duplicados (RF-ING-01 a 04, RF-EGR-01 a 04 y RF-TRA-01).
 */
public class MovimientoService {

    private final Database db;
    private final MovimientoDao movimientos;
    private final CategoriaDao categorias;
    private final BitacoraService bitacora;

    public MovimientoService(Database db, MovimientoDao movimientos, CategoriaDao categorias,
            BitacoraService bitacora) {
        this.db = db;
        this.movimientos = movimientos;
        this.categorias = categorias;
        this.bitacora = bitacora;
    }

    public List<String> categorias(TipoMovimiento tipo) {
        return categorias.listar(tipo);
    }

    /**
     * Guarda un movimiento nuevo. Si hay uno parecido y no se confirmo, lanza
     * {@link PosibleDuplicadoException}.
     */
    public Movimiento registrar(Movimiento nuevo, boolean confirmarDuplicado) {
        Movimiento m = normalizar(nuevo);
        validar(m);
        if (!confirmarDuplicado) {
            List<Movimiento> parecidos = movimientos.posiblesDuplicados(m.conId(0));
            if (!parecidos.isEmpty()) {
                throw new PosibleDuplicadoException(parecidos);
            }
        }
        return db.transaccion(() -> {
            long id = movimientos.insertar(m);
            Movimiento guardado = m.conId(id);
            bitacora.registrar(m.tipo().name(), "ALTA", m.monto(), descripcion(guardado));
            return guardado;
        });
    }

    public Movimiento editar(Movimiento cambios) {
        Movimiento m = normalizar(cambios);
        validar(m);
        Movimiento anterior = movimientos.buscarPorId(m.id())
                .orElseThrow(() -> new AppException("El movimiento ya no existe."));
        if (anterior.cancelado()) {
            throw new AppException("Un movimiento cancelado no se puede editar.");
        }
        return db.transaccion(() -> {
            movimientos.actualizar(m);
            bitacora.registrar(m.tipo().name(), "EDICION", m.monto(),
                    "Antes: " + descripcion(anterior) + " | Ahora: " + descripcion(m));
            return m;
        });
    }

    public void cancelar(long id) {
        Movimiento actual = movimientos.buscarPorId(id)
                .orElseThrow(() -> new AppException("El movimiento ya no existe."));
        if (actual.cancelado()) {
            throw new AppException("El movimiento ya estaba cancelado.");
        }
        db.transaccion(() -> {
            movimientos.actualizar(actual.comoCancelado());
            bitacora.registrar(actual.tipo().name(), "CANCELACION", actual.monto(), descripcion(actual));
            return null;
        });
    }

    public List<Movimiento> buscar(FiltroMovimiento filtro) {
        return movimientos.buscar(filtro);
    }

    /** Guarda un movimiento sin pedir confirmacion; lo usa el modulo de pendientes. */
    Movimiento registrarSinRevisarDuplicado(Movimiento nuevo) {
        return registrar(nuevo, true);
    }

    private Movimiento normalizar(Movimiento m) {
        BigDecimal monto = m.monto() == null ? null : m.monto().setScale(2, RoundingMode.HALF_UP);
        return new Movimiento(m.id(), m.tipo(), m.fecha(), monto, limpiar(m.concepto()), m.categoria(),
                limpiar(m.usuario()), m.formaPago(), limpiar(m.proveedor()), m.cancelado());
    }

    private void validar(Movimiento m) {
        List<String> errores = new ArrayList<>();
        if (m.tipo() == null) {
            errores.add("Falta indicar si es ingreso o egreso.");
        }
        if (m.fecha() == null) {
            errores.add("Falta la fecha.");
        }
        if (m.monto() == null || m.monto().signum() <= 0) {
            errores.add("El monto debe ser mayor que cero.");
        }
        if (m.concepto() == null || m.concepto().isEmpty()) {
            errores.add("Falta el concepto.");
        }
        if (m.tipo() != null && (m.categoria() == null || !categorias.listar(m.tipo()).contains(m.categoria()))) {
            errores.add("Elige una categoría de la lista.");
        }
        if (m.tipo() == TipoMovimiento.INGRESO) {
            if (m.usuario() == null || m.usuario().isEmpty()) {
                errores.add("Falta el usuario atendido.");
            }
            if (m.formaPago() == null) {
                errores.add("Falta la forma de pago.");
            }
        }
        if (!errores.isEmpty()) {
            throw new AppException(String.join("\n", errores));
        }
    }

    private static String descripcion(Movimiento m) {
        String persona = m.persona().isEmpty() ? "" : " (" + m.persona() + ")";
        return m.tipo().etiqueta() + " " + Formatos.fecha(m.fecha()) + " " + Formatos.moneda(m.monto())
                + " - " + m.concepto() + " [" + m.categoria() + "]" + persona;
    }

    private static String limpiar(String texto) {
        return texto == null ? null : texto.trim().replaceAll("\\s+", " ");
    }
}
