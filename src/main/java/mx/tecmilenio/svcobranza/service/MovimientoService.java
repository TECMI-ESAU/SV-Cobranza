package mx.tecmilenio.svcobranza.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.db.CategoriaDao;
import mx.tecmilenio.svcobranza.db.Database;
import mx.tecmilenio.svcobranza.db.MovimientoDao;
import mx.tecmilenio.svcobranza.db.UsuarioDao;
import mx.tecmilenio.svcobranza.model.Categorias;
import mx.tecmilenio.svcobranza.model.FiltroMovimiento;
import mx.tecmilenio.svcobranza.model.FormaPago;
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
    private final UsuarioDao usuarios;
    private final BitacoraService bitacora;

    public MovimientoService(Database db, MovimientoDao movimientos, CategoriaDao categorias,
            UsuarioDao usuarios, BitacoraService bitacora) {
        this.db = db;
        this.movimientos = movimientos;
        this.categorias = categorias;
        this.usuarios = usuarios;
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
        validarUsuarioRegistrado(m);
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
        // Si el usuario ya se dio de baja, su historial se puede corregir sin cambiar el nombre
        if (!java.util.Objects.equals(anterior.usuario(), m.usuario())) {
            validarUsuarioRegistrado(m);
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
        String concepto = limpiar(m.concepto());
        if (concepto == null && m.tipo() == TipoMovimiento.EGRESO) {
            concepto = "";
        }
        String usuario = limpiar(m.usuario());
        if (usuario != null && usuario.isEmpty()) {
            usuario = null;
        }
        // El egreso solo lleva usuario en las donaciones, y la forma de pago es solo de ingresos
        if (m.tipo() == TipoMovimiento.EGRESO && !Categorias.DONACION.equals(m.categoria())) {
            usuario = null;
        }
        FormaPago forma = m.tipo() == TipoMovimiento.INGRESO ? m.formaPago() : null;
        return new Movimiento(m.id(), m.tipo(), m.fecha(), monto, concepto, m.categoria(), usuario, forma,
                m.cancelado());
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
        if (m.tipo() == TipoMovimiento.INGRESO && (m.concepto() == null || m.concepto().isEmpty())) {
            errores.add("Falta el concepto.");
        }
        if (m.tipo() != null && (m.categoria() == null || !categorias.listar(m.tipo()).contains(m.categoria()))) {
            errores.add("Elige una categoría de la lista.");
        }
        if (m.tipo() == TipoMovimiento.INGRESO) {
            // "Otros" es el unico ingreso que puede no venir de un usuario registrado
            if (m.usuario() == null && !Categorias.OTROS.equals(m.categoria())) {
                errores.add("Falta el usuario atendido.");
            }
            if (m.formaPago() == null) {
                errores.add("Falta la forma de pago.");
            }
        } else if (m.tipo() == TipoMovimiento.EGRESO && Categorias.DONACION.equals(m.categoria())
                && m.usuario() == null) {
            errores.add("Falta el usuario que recibe la donación.");
        }
        if (!errores.isEmpty()) {
            throw new AppException(String.join("\n", errores));
        }
    }

    private void validarUsuarioRegistrado(Movimiento m) {
        if (m.usuario() != null && usuarios.buscarPorNombre(m.usuario()).isEmpty()) {
            throw new AppException("El usuario \"" + m.usuario() + "\" no está registrado. "
                    + "Elígelo de la lista o regístralo primero en Nuevo Usuario.");
        }
    }

    private static String descripcion(Movimiento m) {
        String persona = m.persona().isEmpty() ? "" : " (" + m.persona() + ")";
        String concepto = m.concepto() == null || m.concepto().isEmpty() ? "" : " - " + m.concepto();
        return m.tipo().etiqueta() + " " + Formatos.fecha(m.fecha()) + " " + Formatos.moneda(m.monto())
                + concepto + " [" + m.categoria() + "]" + persona;
    }

    private static String limpiar(String texto) {
        return texto == null ? null : texto.trim().replaceAll("\\s+", " ");
    }
}
