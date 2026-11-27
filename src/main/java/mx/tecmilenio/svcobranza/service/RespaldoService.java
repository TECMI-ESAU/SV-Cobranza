package mx.tecmilenio.svcobranza.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.db.BitacoraDao;
import mx.tecmilenio.svcobranza.db.ConfiguracionDao;
import mx.tecmilenio.svcobranza.db.Database;
import mx.tecmilenio.svcobranza.db.ExperienciaDao;
import mx.tecmilenio.svcobranza.db.MovimientoDao;
import mx.tecmilenio.svcobranza.db.PendienteDao;
import mx.tecmilenio.svcobranza.db.UsuarioDao;
import mx.tecmilenio.svcobranza.model.EntradaBitacora;
import mx.tecmilenio.svcobranza.model.EstadoPendiente;
import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Frecuencia;
import mx.tecmilenio.svcobranza.model.Movimiento;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.Servicio;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;
import mx.tecmilenio.svcobranza.model.TipoServicio;
import mx.tecmilenio.svcobranza.model.UsuarioAtendido;

/**
 * Respaldo y restauracion (RF-RES-01, RF-RES-02, SEG-03, SEG-04). El respaldo es un CSV
 * con todos los datos, cifrado con una contrasena que el usuario escribe. Con el mismo
 * archivo y la contrasena se restaura en cualquier computadora. Formato version 2 (v2.0):
 * no se pueden restaurar respaldos de la version 1.
 */
public class RespaldoService {

    private static final String ENCABEZADO = "SVCOBRANZA";
    private static final String VERSION = "2";
    private static final int CONTRASENA_MINIMA = 4;

    private final Database db;
    private final UsuarioDao usuarios;
    private final MovimientoDao movimientos;
    private final PendienteDao pendientes;
    private final ExperienciaDao experiencia;
    private final ConfiguracionDao configuracion;
    private final BitacoraDao bitacoraDao;
    private final BitacoraService bitacora;

    public RespaldoService(Database db, UsuarioDao usuarios, MovimientoDao movimientos, PendienteDao pendientes,
            ExperienciaDao experiencia, ConfiguracionDao configuracion, BitacoraDao bitacoraDao,
            BitacoraService bitacora) {
        this.db = db;
        this.usuarios = usuarios;
        this.movimientos = movimientos;
        this.pendientes = pendientes;
        this.experiencia = experiencia;
        this.configuracion = configuracion;
        this.bitacoraDao = bitacoraDao;
        this.bitacora = bitacora;
    }

    /** Genera el respaldo cifrado en el archivo indicado. */
    public void generar(Path destino, char[] contrasena) {
        validarContrasena(contrasena);
        byte[] cifrado = CifradoRespaldo.cifrar(construirCsv().getBytes(StandardCharsets.UTF_8), contrasena);
        try {
            Files.write(destino, cifrado);
        } catch (IOException e) {
            throw new AppException("No se pudo guardar el respaldo en " + destino, e);
        }
        bitacora.registrar("RESPALDO", "GENERACION", null, "Respaldo guardado en " + destino.getFileName());
    }

    /** Reemplaza todos los datos actuales por los del respaldo. */
    public void restaurar(Path origen, char[] contrasena) {
        validarContrasena(contrasena);
        byte[] archivo;
        try {
            archivo = Files.readAllBytes(origen);
        } catch (IOException e) {
            throw new AppException("No se pudo leer el archivo " + origen, e);
        }
        String csv = new String(CifradoRespaldo.descifrar(archivo, contrasena), StandardCharsets.UTF_8);
        List<List<String>> filas = Csv.leer(csv);
        if (filas.isEmpty() || !ENCABEZADO.equals(filas.get(0).get(0))) {
            throw new AppException("El archivo no es un respaldo de SV-Cobranza.");
        }
        if (filas.get(0).size() < 2 || !VERSION.equals(filas.get(0).get(1))) {
            throw new AppException("El respaldo es de una versión anterior del sistema y no se puede restaurar.");
        }
        // Se interpreta todo antes de borrar, para no perder datos si el archivo esta mal
        List<Object[]> users = new ArrayList<>();
        List<Object[]> movs = new ArrayList<>();
        List<Object[]> pends = new ArrayList<>();
        List<LocalDate> fechasExperiencia = new ArrayList<>();
        Map<String, String> config = new HashMap<>();
        List<EntradaBitacora> bits = new ArrayList<>();
        try {
            for (List<String> f : filas.subList(1, filas.size())) {
                switch (f.get(0)) {
                    case "USUARIO" -> users.add(new Object[] { Long.parseLong(f.get(1)), leerUsuario(f) });
                    case "SERVICIO" -> agregarServicio(users, f);
                    case "EXPERIENCIA" -> fechasExperiencia.add(LocalDate.parse(f.get(1)));
                    case "CONFIG" -> config.put(f.get(1), f.get(2));
                    case "MOVIMIENTO" -> movs.add(new Object[] { Long.parseLong(f.get(1)), leerMovimiento(f) });
                    case "PENDIENTE" -> pends.add(new Object[] { Long.parseLong(f.get(2)), leerPendiente(f) });
                    case "BITACORA" -> bits.add(new EntradaBitacora(0, LocalDateTime.parse(f.get(1)), f.get(2),
                            f.get(3), decimal(f.get(4)), f.get(5)));
                    default -> throw new AppException("Registro desconocido en el respaldo: " + f.get(0));
                }
            }
        } catch (RuntimeException e) {
            if (e instanceof AppException) {
                throw e;
            }
            throw new AppException("El respaldo está dañado o es de otra versión.", e);
        }
        db.transaccion(() -> {
            pendientes.borrarTodo();
            movimientos.borrarTodo();
            usuarios.borrarTodo();
            experiencia.borrarTodo();
            configuracion.borrarTodo();
            bitacoraDao.borrarTodo();
            Map<Long, Long> idsUsuario = new HashMap<>();
            for (Object[] par : users) {
                idsUsuario.put((Long) par[0], usuarios.insertar((UsuarioAtendido) par[1]));
            }
            Map<Long, Long> idsMovimiento = new HashMap<>();
            for (Object[] par : movs) {
                idsMovimiento.put((Long) par[0], movimientos.insertar((Movimiento) par[1]));
            }
            for (Object[] par : pends) {
                Pendiente p = (Pendiente) par[1];
                Long usuarioId = idsUsuario.get((Long) par[0]);
                if (usuarioId == null) {
                    throw new AppException("El respaldo está dañado: un cobro apunta a un usuario inexistente.");
                }
                Long movimientoId = p.movimientoId() == null ? null : idsMovimiento.get(p.movimientoId());
                pendientes.insertar(new Pendiente(0, usuarioId, p.usuario(), p.categoria(), p.fechaLimite(),
                        p.monto(), p.estado(), p.motivo(), movimientoId));
            }
            for (LocalDate fecha : fechasExperiencia) {
                experiencia.insertar(fecha);
            }
            config.forEach(configuracion::guardar);
            for (EntradaBitacora b : bits) {
                bitacoraDao.registrar(b.fechaHora(), b.tipo(), b.accion(), b.monto(), b.detalle());
            }
            bitacora.registrar("RESPALDO", "RESTAURACION", null,
                    users.size() + " usuarios, " + movs.size() + " movimientos y " + pends.size()
                            + " cobros restaurados desde " + origen.getFileName());
            return null;
        });
    }

    private String construirCsv() {
        StringBuilder csv = new StringBuilder(Csv.linea(List.of(ENCABEZADO, VERSION)));
        for (UsuarioAtendido u : usuarios.listar()) {
            csv.append(Csv.linea(List.of("USUARIO", String.valueOf(u.id()), u.nombre(), u.fechaIngreso().toString(),
                    u.fechaRegistro().toString(), nulo(u.esquema()), String.valueOf(u.becado()),
                    u.frecuenciaEstancia() == null ? "" : u.frecuenciaEstancia().name(), texto(u.montoEstancia()),
                    u.fechaBase() == null ? "" : u.fechaBase().toString(),
                    u.diaMes() == null ? "" : String.valueOf(u.diaMes()), String.valueOf(u.experiencia()),
                    texto(u.montoExperiencia()), texto(u.montoTiendita()))));
            for (Servicio s : u.servicios()) {
                csv.append(Csv.linea(List.of("SERVICIO", String.valueOf(u.id()), s.tipo().name(),
                        s.monto().toPlainString(), s.fechaInicio() == null ? "" : s.fechaInicio().toString(),
                        s.recurrencia().name())));
            }
        }
        for (LocalDate fecha : experiencia.listar()) {
            csv.append(Csv.linea(List.of("EXPERIENCIA", fecha.toString())));
        }
        for (Map.Entry<String, String> e : configuracion.todas().entrySet()) {
            csv.append(Csv.linea(List.of("CONFIG", e.getKey(), e.getValue())));
        }
        for (Movimiento m : movimientos.listarTodos()) {
            csv.append(Csv.linea(List.of("MOVIMIENTO", String.valueOf(m.id()), m.tipo().name(),
                    m.fecha().toString(), m.monto().toPlainString(), nulo(m.concepto()), m.categoria(),
                    nulo(m.usuario()), m.formaPago() == null ? "" : m.formaPago().name(),
                    String.valueOf(m.cancelado()))));
        }
        for (Pendiente p : pendientes.listarTodos()) {
            csv.append(Csv.linea(List.of("PENDIENTE", String.valueOf(p.id()), String.valueOf(p.usuarioId()),
                    p.categoria(), p.fechaLimite().toString(), p.monto().toPlainString(), p.estado().name(),
                    nulo(p.motivo()), p.movimientoId() == null ? "" : String.valueOf(p.movimientoId()))));
        }
        List<EntradaBitacora> bits = new ArrayList<>(bitacoraDao.listar());
        java.util.Collections.reverse(bits);
        for (EntradaBitacora b : bits) {
            csv.append(Csv.linea(List.of("BITACORA", b.fechaHora().toString(), b.tipo(), b.accion(),
                    b.monto() == null ? "" : b.monto().toPlainString(), nulo(b.detalle()))));
        }
        return csv.toString();
    }

    private static UsuarioAtendido leerUsuario(List<String> f) {
        return new UsuarioAtendido(0, f.get(2), LocalDate.parse(f.get(3)), LocalDate.parse(f.get(4)),
                vacioANulo(f.get(5)), Boolean.parseBoolean(f.get(6)),
                f.get(7).isEmpty() ? null : Frecuencia.valueOf(f.get(7)), decimal(f.get(8)),
                f.get(9).isEmpty() ? null : LocalDate.parse(f.get(9)),
                f.get(10).isEmpty() ? null : Integer.valueOf(f.get(10)), Boolean.parseBoolean(f.get(11)),
                decimal(f.get(12)), decimal(f.get(13)), List.of());
    }

    /** Agrega un servicio al ultimo usuario leido con ese id de respaldo. */
    private static void agregarServicio(List<Object[]> users, List<String> f) {
        long idRespaldo = Long.parseLong(f.get(1));
        for (Object[] par : users) {
            if ((Long) par[0] == idRespaldo) {
                UsuarioAtendido u = (UsuarioAtendido) par[1];
                List<Servicio> servicios = new ArrayList<>(u.servicios());
                servicios.add(new Servicio(TipoServicio.valueOf(f.get(2)), new BigDecimal(f.get(3)),
                        f.get(4).isEmpty() ? null : LocalDate.parse(f.get(4)), Frecuencia.valueOf(f.get(5))));
                par[1] = new UsuarioAtendido(u.id(), u.nombre(), u.fechaIngreso(), u.fechaRegistro(), u.esquema(),
                        u.becado(), u.frecuenciaEstancia(), u.montoEstancia(), u.fechaBase(), u.diaMes(),
                        u.experiencia(), u.montoExperiencia(), u.montoTiendita(), servicios);
                return;
            }
        }
        throw new AppException("El respaldo está dañado: un servicio apunta a un usuario inexistente.");
    }

    private static Movimiento leerMovimiento(List<String> f) {
        return new Movimiento(0, TipoMovimiento.valueOf(f.get(2)), LocalDate.parse(f.get(3)),
                new BigDecimal(f.get(4)), f.get(5), f.get(6), vacioANulo(f.get(7)),
                f.get(8).isEmpty() ? null : FormaPago.valueOf(f.get(8)), Boolean.parseBoolean(f.get(9)));
    }

    private static Pendiente leerPendiente(List<String> f) {
        return new Pendiente(0, 0, "", f.get(3), LocalDate.parse(f.get(4)), new BigDecimal(f.get(5)),
                EstadoPendiente.valueOf(f.get(6)), vacioANulo(f.get(7)),
                f.get(8).isEmpty() ? null : Long.parseLong(f.get(8)));
    }

    private static BigDecimal decimal(String texto) {
        return texto.isEmpty() ? null : new BigDecimal(texto);
    }

    private static String texto(BigDecimal monto) {
        return monto == null ? "" : monto.toPlainString();
    }

    private static String nulo(String s) {
        return s == null ? "" : s;
    }

    private static String vacioANulo(String s) {
        return s.isEmpty() ? null : s;
    }

    private static void validarContrasena(char[] contrasena) {
        if (contrasena == null || contrasena.length < CONTRASENA_MINIMA) {
            throw new AppException("La contraseña del respaldo debe tener al menos " + CONTRASENA_MINIMA
                    + " caracteres.");
        }
    }
}
