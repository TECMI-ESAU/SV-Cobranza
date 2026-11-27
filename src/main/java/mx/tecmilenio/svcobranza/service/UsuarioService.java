package mx.tecmilenio.svcobranza.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.db.Database;
import mx.tecmilenio.svcobranza.db.MovimientoDao;
import mx.tecmilenio.svcobranza.db.PendienteDao;
import mx.tecmilenio.svcobranza.db.UsuarioDao;
import mx.tecmilenio.svcobranza.model.EstadoPendiente;
import mx.tecmilenio.svcobranza.model.Frecuencia;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.Servicio;
import mx.tecmilenio.svcobranza.model.TipoServicio;
import mx.tecmilenio.svcobranza.model.UsuarioAtendido;
import mx.tecmilenio.svcobranza.ui.Formatos;

/** Registro, edicion y baja de usuarios atendidos (V2-01). */
public class UsuarioService {

    /** Dia del mes que se usa en la estancia mensual cuando no se elige otro. */
    public static final int DIA_MES_POR_DEFECTO = 1;

    private final Database db;
    private final UsuarioDao usuarios;
    private final MovimientoDao movimientos;
    private final PendienteDao pendientes;
    private final BitacoraService bitacora;
    private final Clock reloj;

    public UsuarioService(Database db, UsuarioDao usuarios, MovimientoDao movimientos, PendienteDao pendientes,
            BitacoraService bitacora, Clock reloj) {
        this.db = db;
        this.usuarios = usuarios;
        this.movimientos = movimientos;
        this.pendientes = pendientes;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    /** Da de alta al usuario; la fecha de registro es la de hoy. */
    public UsuarioAtendido registrar(UsuarioAtendido nuevo) {
        UsuarioAtendido u = normalizar(nuevo).conId(0).conFechaRegistro(LocalDate.now(reloj));
        validar(u);
        verificarNombreLibre(u.nombre(), 0);
        return db.transaccion(() -> {
            long id = usuarios.insertar(u);
            UsuarioAtendido guardado = u.conId(id);
            bitacora.registrar("USUARIO", "ALTA", null, descripcion(guardado));
            return guardado;
        });
    }

    /**
     * Guarda los cambios. Conserva la fecha de registro original. Si cambia el nombre, los
     * ingresos y egresos del usuario pasan al nombre nuevo para no perder su historial.
     */
    public UsuarioAtendido editar(UsuarioAtendido cambios) {
        UsuarioAtendido anterior = usuarios.buscarPorId(cambios.id())
                .orElseThrow(() -> new AppException("El usuario ya no existe."));
        UsuarioAtendido u = normalizar(cambios).conFechaRegistro(anterior.fechaRegistro());
        validar(u);
        verificarNombreLibre(u.nombre(), u.id());
        return db.transaccion(() -> {
            usuarios.actualizar(u);
            if (!anterior.nombre().equals(u.nombre())) {
                movimientos.renombrarUsuario(anterior.nombre(), u.nombre());
            }
            bitacora.registrar("USUARIO", "EDICION", null,
                    "Antes: " + descripcion(anterior) + " | Ahora: " + descripcion(u));
            return u;
        });
    }

    /**
     * Borra al usuario y sus cobros. Los ingresos que ya pago se conservan, porque guardan su
     * nombre como texto.
     */
    public void borrar(long id) {
        UsuarioAtendido u = usuarios.buscarPorId(id).orElseThrow(() -> new AppException("El usuario ya no existe."));
        long sinPagar = pendientes.porUsuario(id).stream().filter(p -> p.estado() == EstadoPendiente.PENDIENTE)
                .count();
        db.transaccion(() -> {
            usuarios.borrar(id);
            bitacora.registrar("USUARIO", "BAJA", null,
                    descripcion(u) + " | " + sinPagar + " cobros pendientes eliminados");
            return null;
        });
    }

    public Optional<UsuarioAtendido> buscarPorId(long id) {
        return usuarios.buscarPorId(id);
    }

    public Optional<UsuarioAtendido> buscarPorNombre(String nombre) {
        return nombre == null || nombre.isBlank() ? Optional.empty() : usuarios.buscarPorNombre(nombre);
    }

    /** Todos los usuarios, ordenados por nombre. */
    public List<UsuarioAtendido> listar() {
        return usuarios.listar();
    }

    /** Cobros de un usuario, de todos los estados. */
    public List<Pendiente> cobrosDe(long usuarioId) {
        return pendientes.porUsuario(usuarioId);
    }

    private void verificarNombreLibre(String nombre, long idPropio) {
        Optional<UsuarioAtendido> existente = usuarios.buscarPorNombre(nombre);
        if (existente.isPresent() && existente.get().id() != idPropio) {
            throw new AppException("Ya existe un usuario con el nombre \"" + existente.get().nombre() + "\".");
        }
    }

    /** Limpia espacios y deja en blanco lo que no aplica (becado, frecuencia de la estancia). */
    private static UsuarioAtendido normalizar(UsuarioAtendido u) {
        String nombre = limpiar(u.nombre());
        String esquema = limpiar(u.esquema());
        if (esquema != null && esquema.isEmpty()) {
            esquema = null;
        }
        BigDecimal tiendita = escala(u.montoTiendita());
        if (u.becado()) {
            return new UsuarioAtendido(u.id(), nombre, u.fechaIngreso(), u.fechaRegistro(), esquema, true, null,
                    null, null, null, false, null, tiendita, List.of());
        }
        Frecuencia frecuencia = u.frecuenciaEstancia();
        LocalDate fechaBase = frecuencia == Frecuencia.SEMANAL ? u.fechaBase() : null;
        Integer diaMes = null;
        if (frecuencia == Frecuencia.MENSUAL) {
            diaMes = u.diaMes() == null ? DIA_MES_POR_DEFECTO : u.diaMes();
        }
        BigDecimal montoExperiencia = u.experiencia() ? escala(u.montoExperiencia()) : null;
        List<Servicio> servicios = new ArrayList<>();
        for (Servicio s : u.servicios()) {
            servicios.add(new Servicio(s.tipo(), escala(s.monto()), s.fechaInicio(), s.recurrencia()));
        }
        return new UsuarioAtendido(u.id(), nombre, u.fechaIngreso(), u.fechaRegistro(), esquema, false, frecuencia,
                escala(u.montoEstancia()), fechaBase, diaMes, u.experiencia(), montoExperiencia, tiendita,
                servicios);
    }

    private static void validar(UsuarioAtendido u) {
        List<String> errores = new ArrayList<>();
        if (u.nombre() == null || u.nombre().isEmpty()) {
            errores.add("Falta el nombre del usuario.");
        }
        if (u.fechaIngreso() == null) {
            errores.add("Falta la fecha de ingreso.");
        }
        if (u.montoTiendita() != null && u.montoTiendita().signum() < 0) {
            errores.add("El monto de tiendita no puede ser negativo.");
        }
        if (!u.becado()) {
            validarEstancia(u, errores);
            if (u.experiencia() && (u.montoExperiencia() == null || u.montoExperiencia().signum() <= 0)) {
                errores.add("El monto de la experiencia debe ser mayor que cero.");
            }
            validarServicios(u.servicios(), errores);
        }
        if (!errores.isEmpty()) {
            throw new AppException(String.join("\n", errores));
        }
    }

    private static void validarEstancia(UsuarioAtendido u, List<String> errores) {
        if (u.frecuenciaEstancia() == null) {
            errores.add("Elige si la estancia se cobra semanal o mensual.");
        }
        if (u.montoEstancia() == null || u.montoEstancia().signum() <= 0) {
            errores.add("El monto de la estancia debe ser mayor que cero.");
        }
        if (u.frecuenciaEstancia() == Frecuencia.SEMANAL && u.fechaBase() == null) {
            errores.add("Falta la fecha desde la que se cuenta el cobro semanal.");
        }
        if (u.frecuenciaEstancia() == Frecuencia.MENSUAL && (u.diaMes() < 1 || u.diaMes() > 31)) {
            errores.add("El día del mes debe estar entre 1 y 31.");
        }
    }

    private static void validarServicios(List<Servicio> servicios, List<String> errores) {
        Set<TipoServicio> vistos = EnumSet.noneOf(TipoServicio.class);
        for (Servicio s : servicios) {
            if (s.tipo() == null) {
                errores.add("Falta indicar el tipo de servicio.");
                continue;
            }
            if (!vistos.add(s.tipo())) {
                errores.add("El servicio " + s.tipo() + " está repetido.");
            }
            if (s.monto() == null || s.monto().signum() <= 0) {
                errores.add("El monto del servicio " + s.tipo() + " debe ser mayor que cero.");
            }
            if (s.recurrencia() == null) {
                errores.add("Elige si el servicio " + s.tipo() + " se cobra semanal o mensual.");
            }
        }
    }

    private static String descripcion(UsuarioAtendido u) {
        String estancia = u.becado() ? "becado"
                : u.frecuenciaEstancia() + " " + Formatos.moneda(u.montoEstancia());
        return u.nombre() + " (ingreso " + Formatos.fecha(u.fechaIngreso()) + ", " + estancia + ")";
    }

    private static BigDecimal escala(BigDecimal monto) {
        return monto == null ? null : monto.setScale(2, RoundingMode.HALF_UP);
    }

    private static String limpiar(String texto) {
        return texto == null ? null : texto.trim().replaceAll("\\s+", " ");
    }
}
