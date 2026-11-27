package mx.tecmilenio.svcobranza.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.db.ConfiguracionDao;
import mx.tecmilenio.svcobranza.db.Database;
import mx.tecmilenio.svcobranza.db.ExperienciaDao;
import mx.tecmilenio.svcobranza.db.PendienteDao;
import mx.tecmilenio.svcobranza.db.UsuarioDao;
import mx.tecmilenio.svcobranza.model.Categorias;
import mx.tecmilenio.svcobranza.model.EstadoPendiente;
import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Frecuencia;
import mx.tecmilenio.svcobranza.model.Movimiento;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.Servicio;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;
import mx.tecmilenio.svcobranza.model.UsuarioAtendido;
import mx.tecmilenio.svcobranza.ui.Formatos;

/**
 * Cobros a usuarios (V2-03 y V2-04). Genera los cobros de los proximos 30 dias de cada
 * usuario, sin duplicados, al abrir la aplicacion y al crear o editar un usuario; los que
 * cayeron con la aplicacion cerrada quedan vencidos con su fecha calculada. Tambien paga,
 * condona y revierte cobros.
 */
public class CobroService {

    /** Dias hacia adelante para los que se generan y muestran los cobros. */
    public static final int HORIZONTE_DIAS = 30;

    /** Clave de configuracion: desde cuando se cobra la experiencia (ultimo cambio del calendario). */
    public static final String CLAVE_EXPERIENCIA_DESDE = "experiencia_desde";

    private final Database db;
    private final UsuarioDao usuarios;
    private final PendienteDao pendientes;
    private final ExperienciaDao experiencia;
    private final ConfiguracionDao configuracion;
    private final MovimientoService movimientos;
    private final BitacoraService bitacora;
    private final Clock reloj;

    public CobroService(Database db, UsuarioDao usuarios, PendienteDao pendientes, ExperienciaDao experiencia,
            ConfiguracionDao configuracion, MovimientoService movimientos, BitacoraService bitacora, Clock reloj) {
        this.db = db;
        this.usuarios = usuarios;
        this.pendientes = pendientes;
        this.experiencia = experiencia;
        this.configuracion = configuracion;
        this.movimientos = movimientos;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    /** Genera los cobros que falten de todos los usuarios. Es seguro llamarlo las veces que sea. */
    public void generarTodos() {
        db.transaccion(() -> {
            for (UsuarioAtendido u : usuarios.listar()) {
                insertarNuevos(u, candidatos(u, usuarios.cobrosDesde(u.id())));
            }
            return null;
        });
    }

    /** Genera los cobros que falten de un usuario (por ejemplo, recien registrado). */
    public void generarPara(UsuarioAtendido u) {
        db.transaccion(() -> {
            insertarNuevos(u, candidatos(u, usuarios.cobrosDesde(u.id())));
            return null;
        });
    }

    /**
     * Despues de editar a un usuario: reemplaza sus cobros Pendiente de hoy en adelante por los
     * del esquema nuevo. Los vencidos, pagados y condonados se conservan, y el esquema nuevo
     * rige desde hoy (el monto nuevo aplica solo a cobros futuros).
     */
    public void reemplazarAlEditar(UsuarioAtendido u) {
        db.transaccion(() -> {
            LocalDate hoy = LocalDate.now(reloj);
            pendientes.borrarPendientesDesde(u.id(), hoy);
            usuarios.fijarCobrosDesde(u.id(), hoy);
            insertarNuevos(u, candidatos(u, hoy));
            return null;
        });
    }

    /**
     * Despues de capturar o editar una fecha del calendario de experiencia: reemplaza los
     * cobros de Experiencia Pendiente de hoy en adelante por los de las fechas nuevas. Las
     * fechas anteriores a hoy no generan cobro; los vencidos, pagados y condonados no se tocan.
     */
    public void reemplazarExperiencia() {
        db.transaccion(() -> {
            LocalDate hoy = LocalDate.now(reloj);
            configuracion.guardar(CLAVE_EXPERIENCIA_DESDE, hoy.toString());
            pendientes.borrarPendientesDeCategoriaDesde(Categorias.EXPERIENCIA, hoy);
            for (UsuarioAtendido u : usuarios.listar()) {
                List<Pendiente> soloExperiencia = candidatos(u, usuarios.cobrosDesde(u.id())).stream()
                        .filter(p -> Categorias.EXPERIENCIA.equals(p.categoria())).toList();
                insertarNuevos(u, soloExperiencia);
            }
            return null;
        });
    }

    /** Cobros que muestra la pestaña Cobros pendientes: vencidos y los de los proximos 30 dias. */
    public List<Pendiente> visibles() {
        return pendientes.pendientesHasta(LocalDate.now(reloj).plusDays(HORIZONTE_DIAS));
    }

    // ---------- pagar, condonar, revertir ----------

    /**
     * Paga un cobro: crea el ingreso a nombre del usuario (con la fecha, forma de pago y monto
     * indicados) y marca el cobro como Pagado. Devuelve el ingreso creado.
     */
    public Movimiento pagar(long pendienteId, LocalDate fechaPago, FormaPago forma, BigDecimal monto) {
        Pendiente p = buscarPendiente(pendienteId);
        if (fechaPago == null) {
            throw new AppException("Falta la fecha de pago.");
        }
        if (forma == null) {
            throw new AppException("Falta la forma de pago.");
        }
        BigDecimal montoPagado = monto == null ? null : monto.setScale(2, RoundingMode.HALF_UP);
        if (montoPagado == null || montoPagado.signum() <= 0) {
            throw new AppException("El monto debe ser mayor que cero.");
        }
        return db.transaccion(() -> {
            Movimiento ingreso = movimientos.registrarSinRevisarDuplicado(new Movimiento(0, TipoMovimiento.INGRESO,
                    fechaPago, montoPagado, concepto(p), p.categoria(), p.usuario(), forma, false));
            pendientes.actualizar(p.comoPagado(ingreso.id()));
            bitacora.registrar("COBRO", "PAGO", montoPagado,
                    p.usuario() + ": " + p.categoria() + " del " + Formatos.fecha(p.fechaLimite()));
            return ingreso;
        });
    }

    /** Condona un cobro: no genera ingreso ni recordatorio. El motivo es opcional. */
    public void condonar(long pendienteId, String motivo) {
        Pendiente p = buscarPendiente(pendienteId);
        String limpio = motivo == null || motivo.isBlank() ? null : motivo.trim().replaceAll("\\s+", " ");
        db.transaccion(() -> {
            pendientes.actualizar(p.comoCondonado(limpio));
            bitacora.registrar("COBRO", "CONDONACION", p.monto(), p.usuario() + ": " + p.categoria() + " del "
                    + Formatos.fecha(p.fechaLimite()) + (limpio == null ? "" : " | Motivo: " + limpio));
            return null;
        });
    }

    /** Revierte una condonacion: el cobro vuelve a Pendiente. */
    public void revertirCondonacion(long pendienteId) {
        Pendiente p = pendientes.buscarPorId(pendienteId)
                .orElseThrow(() -> new AppException("El cobro ya no existe."));
        if (p.estado() != EstadoPendiente.CONDONADO) {
            throw new AppException("Solo se puede revertir un cobro condonado.");
        }
        db.transaccion(() -> {
            pendientes.actualizar(p.comoPendiente());
            bitacora.registrar("COBRO", "REVERSION", p.monto(), p.usuario() + ": " + p.categoria() + " del "
                    + Formatos.fecha(p.fechaLimite()));
            return null;
        });
    }

    /**
     * Cobro que liquidaria un ingreso capturado a mano: del mismo usuario, la misma categoria y
     * el mismo monto exacto, aun Pendiente. Si hay varios, el mas antiguo.
     */
    public Optional<Pendiente> cobroQueLiquida(Movimiento ingreso) {
        if (ingreso == null || ingreso.tipo() != TipoMovimiento.INGRESO || ingreso.cancelado()
                || ingreso.usuario() == null || ingreso.monto() == null) {
            return Optional.empty();
        }
        return pendientes.porNombreDeUsuario(ingreso.usuario()).stream()
                .filter(p -> p.estado() == EstadoPendiente.PENDIENTE)
                .filter(p -> p.categoria().equals(ingreso.categoria()))
                .filter(p -> p.monto().compareTo(ingreso.monto()) == 0)
                .findFirst();
    }

    /** Marca un cobro como Pagado con un ingreso que ya existe (el usuario confirmo que lo liquida). */
    public void liquidar(long pendienteId, long movimientoId) {
        Pendiente p = buscarPendiente(pendienteId);
        db.transaccion(() -> {
            pendientes.actualizar(p.comoPagado(movimientoId));
            bitacora.registrar("COBRO", "PAGO", p.monto(), p.usuario() + ": " + p.categoria() + " del "
                    + Formatos.fecha(p.fechaLimite()) + " (ingreso capturado a mano)");
            return null;
        });
    }

    private Pendiente buscarPendiente(long id) {
        Pendiente p = pendientes.buscarPorId(id).orElseThrow(() -> new AppException("El cobro ya no existe."));
        if (p.estado() != EstadoPendiente.PENDIENTE) {
            throw new AppException("El cobro ya estaba " + (p.estado() == EstadoPendiente.PAGADO ? "pagado."
                    : "condonado."));
        }
        return p;
    }

    private static String concepto(Pendiente p) {
        return p.categoria() + " del " + Formatos.fecha(p.fechaLimite());
    }

    // ---------- calculo de fechas ----------

    /** Cobros que corresponden al usuario entre "desde" y hoy + 30 dias, esten o no guardados. */
    private List<Pendiente> candidatos(UsuarioAtendido u, LocalDate desde) {
        List<Pendiente> lista = new ArrayList<>();
        if (u.becado()) {
            return lista;
        }
        LocalDate hasta = LocalDate.now(reloj).plusDays(HORIZONTE_DIAS);
        if (u.frecuenciaEstancia() == Frecuencia.SEMANAL && u.fechaBase() != null) {
            agregar(lista, u, Categorias.PAGO_SEMANAL, u.montoEstancia(),
                    semanales(u.fechaBase(), desde, hasta));
        } else if (u.frecuenciaEstancia() == Frecuencia.MENSUAL && u.diaMes() != null) {
            agregar(lista, u, Categorias.PAGO_MENSUAL, u.montoEstancia(),
                    mensuales(u.diaMes(), desde, hasta));
        }
        for (Servicio s : u.servicios()) {
            if (s.fechaInicio() == null) {
                continue; // sin fecha de inicio no se cobra
            }
            List<LocalDate> fechas = s.recurrencia() == Frecuencia.SEMANAL
                    ? semanales(s.fechaInicio(), desde, hasta)
                    : mensuales(s.fechaInicio().getDayOfMonth(), maxima(desde, s.fechaInicio()), hasta);
            agregar(lista, u, s.tipo().categoria(), s.monto(), fechas);
        }
        if (u.experiencia() && u.montoExperiencia() != null) {
            LocalDate inicio = maxima(maxima(desde, u.fechaIngreso()), experienciaDesde());
            List<LocalDate> fechas = experiencia.listar().stream()
                    .filter(f -> !f.isBefore(inicio) && !f.isAfter(hasta)).toList();
            agregar(lista, u, Categorias.EXPERIENCIA, u.montoExperiencia(), fechas);
        }
        return lista;
    }

    private LocalDate experienciaDesde() {
        Optional<String> guardada = configuracion.obtener(CLAVE_EXPERIENCIA_DESDE);
        return guardada.map(LocalDate::parse).orElse(LocalDate.MIN);
    }

    private static void agregar(List<Pendiente> lista, UsuarioAtendido u, String categoria,
            BigDecimal monto, List<LocalDate> fechas) {
        for (LocalDate fecha : fechas) {
            lista.add(new Pendiente(0, u.id(), u.nombre(), categoria, fecha, monto, EstadoPendiente.PENDIENTE,
                    null, null));
        }
    }

    private void insertarNuevos(UsuarioAtendido u, List<Pendiente> candidatos) {
        Set<String> existentes = new HashSet<>();
        for (Pendiente p : pendientes.porUsuario(u.id())) {
            existentes.add(p.categoria() + "|" + p.fechaLimite());
        }
        for (Pendiente p : candidatos) {
            if (existentes.add(p.categoria() + "|" + p.fechaLimite())) {
                pendientes.insertar(p);
            }
        }
    }

    /** Fechas cada 7 dias a partir de "base" (incluida) que caen entre "desde" y "hasta". */
    static List<LocalDate> semanales(LocalDate base, LocalDate desde, LocalDate hasta) {
        List<LocalDate> fechas = new ArrayList<>();
        LocalDate fecha = base;
        if (fecha.isBefore(desde)) {
            long dias = ChronoUnit.DAYS.between(base, desde);
            fecha = base.plusDays(7 * ((dias + 6) / 7));
        }
        for (; !fecha.isAfter(hasta); fecha = fecha.plusDays(7)) {
            fechas.add(fecha);
        }
        return fechas;
    }

    /**
     * El dia indicado de cada mes que cae entre "desde" y "hasta". Si el mes no tiene ese dia,
     * se usa el ultimo dia del mes.
     */
    static List<LocalDate> mensuales(int dia, LocalDate desde, LocalDate hasta) {
        List<LocalDate> fechas = new ArrayList<>();
        YearMonth ultimo = YearMonth.from(hasta);
        for (YearMonth mes = YearMonth.from(desde); !mes.isAfter(ultimo); mes = mes.plusMonths(1)) {
            LocalDate fecha = mes.atDay(Math.min(dia, mes.lengthOfMonth()));
            if (!fecha.isBefore(desde) && !fecha.isAfter(hasta)) {
                fechas.add(fecha);
            }
        }
        return fechas;
    }

    private static LocalDate maxima(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
