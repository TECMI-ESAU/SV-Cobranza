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
import mx.tecmilenio.svcobranza.db.Database;
import mx.tecmilenio.svcobranza.db.MovimientoDao;
import mx.tecmilenio.svcobranza.db.PendienteDao;
import mx.tecmilenio.svcobranza.model.EntradaBitacora;
import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Movimiento;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;
import mx.tecmilenio.svcobranza.model.TipoPendiente;

/**
 * Respaldo y restauracion (RF-RES-01, RF-RES-02, SEG-03, SEG-04). El respaldo es un CSV
 * con todos los datos, cifrado con una contrasena que el usuario escribe. Con el mismo
 * archivo y la contrasena se restaura en cualquier computadora.
 */
public class RespaldoService {

    private static final String ENCABEZADO = "SVCOBRANZA";
    private static final String VERSION = "1";
    private static final int CONTRASENA_MINIMA = 4;

    private final Database db;
    private final MovimientoDao movimientos;
    private final PendienteDao pendientes;
    private final BitacoraDao bitacoraDao;
    private final BitacoraService bitacora;

    public RespaldoService(Database db, MovimientoDao movimientos, PendienteDao pendientes,
            BitacoraDao bitacoraDao, BitacoraService bitacora) {
        this.db = db;
        this.movimientos = movimientos;
        this.pendientes = pendientes;
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
        // Se interpreta todo antes de borrar, para no perder datos si el archivo esta mal
        List<Object[]> movs = new ArrayList<>();
        List<Pendiente> pends = new ArrayList<>();
        List<EntradaBitacora> bits = new ArrayList<>();
        try {
            for (List<String> f : filas.subList(1, filas.size())) {
                switch (f.get(0)) {
                    case "MOVIMIENTO" -> movs.add(new Object[] { Long.parseLong(f.get(1)), leerMovimiento(f) });
                    case "PENDIENTE" -> pends.add(leerPendiente(f));
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
            bitacoraDao.borrarTodo();
            Map<Long, Long> idsNuevos = new HashMap<>();
            for (Object[] par : movs) {
                idsNuevos.put((Long) par[0], movimientos.insertar((Movimiento) par[1]));
            }
            for (Pendiente p : pends) {
                Long movimientoId = p.movimientoId() == null ? null : idsNuevos.get(p.movimientoId());
                pendientes.insertar(new Pendiente(0, p.tipo(), p.fechaLimite(), p.monto(), p.concepto(),
                        p.categoria(), p.contraparte(), p.pagado(), movimientoId));
            }
            for (EntradaBitacora b : bits) {
                bitacoraDao.registrar(b.fechaHora(), b.tipo(), b.accion(), b.monto(), b.detalle());
            }
            bitacora.registrar("RESPALDO", "RESTAURACION", null,
                    movs.size() + " movimientos y " + pends.size() + " pendientes restaurados desde "
                            + origen.getFileName());
            return null;
        });
    }

    private String construirCsv() {
        StringBuilder csv = new StringBuilder(Csv.linea(List.of(ENCABEZADO, VERSION)));
        for (Movimiento m : movimientos.listarTodos()) {
            csv.append(Csv.linea(List.of("MOVIMIENTO", String.valueOf(m.id()), m.tipo().name(),
                    m.fecha().toString(), m.monto().toPlainString(), m.concepto(), m.categoria(),
                    nulo(m.usuario()), m.formaPago() == null ? "" : m.formaPago().name(),
                    nulo(m.proveedor()), String.valueOf(m.cancelado()))));
        }
        for (Pendiente p : pendientes.listarTodos()) {
            csv.append(Csv.linea(List.of("PENDIENTE", String.valueOf(p.id()), p.tipo().name(),
                    p.fechaLimite().toString(), p.monto().toPlainString(), p.concepto(), p.categoria(),
                    p.contraparte(), String.valueOf(p.pagado()),
                    p.movimientoId() == null ? "" : String.valueOf(p.movimientoId()))));
        }
        List<EntradaBitacora> bits = new ArrayList<>(bitacoraDao.listar());
        java.util.Collections.reverse(bits);
        for (EntradaBitacora b : bits) {
            csv.append(Csv.linea(List.of("BITACORA", b.fechaHora().toString(), b.tipo(), b.accion(),
                    b.monto() == null ? "" : b.monto().toPlainString(), nulo(b.detalle()))));
        }
        return csv.toString();
    }

    private static Movimiento leerMovimiento(List<String> f) {
        return new Movimiento(0, TipoMovimiento.valueOf(f.get(2)), LocalDate.parse(f.get(3)),
                new BigDecimal(f.get(4)), f.get(5), f.get(6), vacioANulo(f.get(7)),
                f.get(8).isEmpty() ? null : FormaPago.valueOf(f.get(8)), vacioANulo(f.get(9)),
                Boolean.parseBoolean(f.get(10)));
    }

    private static Pendiente leerPendiente(List<String> f) {
        return new Pendiente(0, TipoPendiente.valueOf(f.get(2)), LocalDate.parse(f.get(3)),
                new BigDecimal(f.get(4)), f.get(5), f.get(6), f.get(7), Boolean.parseBoolean(f.get(8)),
                f.get(9).isEmpty() ? null : Long.parseLong(f.get(9)));
    }

    private static BigDecimal decimal(String texto) {
        return texto.isEmpty() ? null : new BigDecimal(texto);
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
