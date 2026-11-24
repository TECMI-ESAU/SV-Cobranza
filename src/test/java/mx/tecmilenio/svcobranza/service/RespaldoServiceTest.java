package mx.tecmilenio.svcobranza.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.db.BitacoraDao;
import mx.tecmilenio.svcobranza.db.CategoriaDao;
import mx.tecmilenio.svcobranza.db.Database;
import mx.tecmilenio.svcobranza.db.MovimientoDao;
import mx.tecmilenio.svcobranza.db.PendienteDao;
import mx.tecmilenio.svcobranza.db.UsuarioDao;
import mx.tecmilenio.svcobranza.model.FiltroMovimiento;
import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Movimiento;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;
import mx.tecmilenio.svcobranza.model.TipoPendiente;

class RespaldoServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 24);
    private static final char[] CLAVE = "clave-de-prueba".toCharArray();

    /** Una "computadora" completa: su propia base de datos y sus servicios. */
    private static final class Equipo implements AutoCloseable {
        final Database db;
        final BitacoraService bitacora;
        final MovimientoService movimientos;
        final PendienteService pendientes;
        final PendienteDao pendienteDao;
        final RespaldoService respaldo;

        Equipo(String nombre) {
            db = Database.enMemoria(nombre + System.nanoTime());
            ZoneId zona = ZoneId.of("America/Mexico_City");
            Clock reloj = Clock.fixed(HOY.atStartOfDay(zona).toInstant(), zona);
            BitacoraDao bitacoraDao = new BitacoraDao(db);
            MovimientoDao movimientoDao = new MovimientoDao(db, new UsuarioDao(db));
            pendienteDao = new PendienteDao(db);
            bitacora = new BitacoraService(bitacoraDao, reloj);
            movimientos = new MovimientoService(db, movimientoDao, new CategoriaDao(db), bitacora);
            pendientes = new PendienteService(db, pendienteDao, movimientos, bitacora, reloj);
            respaldo = new RespaldoService(db, movimientoDao, pendienteDao, bitacoraDao, bitacora);
        }

        @Override
        public void close() {
            db.close();
        }
    }

    private Equipo origen;

    @BeforeEach
    void preparar() {
        origen = new Equipo("origen");
        origen.movimientos.registrar(new Movimiento(0, TipoMovimiento.INGRESO, HOY, new BigDecimal("1500.50"),
                "Pago con \"comillas\", comas\ny salto de línea", "Tiendita", "Ana, Prueba", FormaPago.EFECTIVO,
                null, false), false);
        origen.movimientos.registrar(new Movimiento(0, TipoMovimiento.EGRESO, HOY, new BigDecimal("320"),
                "Recibo de luz", "Luz", null, null, "CFE de prueba", false), false);
        Movimiento cancelado = origen.movimientos.registrar(new Movimiento(0, TipoMovimiento.INGRESO,
                HOY.minusDays(1), new BigDecimal("90"), "Se cancela", "Otros", "Beto Prueba", FormaPago.DEPOSITO,
                null, false), false);
        origen.movimientos.cancelar(cancelado.id());
        Pendiente p = origen.pendientes.registrar(new Pendiente(0, TipoPendiente.POR_COBRAR, HOY.plusDays(4),
                new BigDecimal("600"), "Experiencia mensual", "Experiencia", "Carla Prueba", false, null));
        Pendiente pagado = origen.pendientes.registrar(new Pendiente(0, TipoPendiente.POR_PAGAR, HOY,
                new BigDecimal("150"), "Internet", "Internet", "ISP de prueba", false, null));
        origen.pendientes.marcarPagado(pagado.id(), HOY, null);
        assertTrue(p.id() > 0);
    }

    @AfterEach
    void cerrar() {
        origen.close();
    }

    // ---------- RF-RES-01 ----------

    @Test
    void generaUnArchivoDeRespaldoQueNoEsTextoLegible(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("respaldo.svb");

        origen.respaldo.generar(archivo, CLAVE);

        assertTrue(Files.exists(archivo));
        String contenido = new String(Files.readAllBytes(archivo), StandardCharsets.ISO_8859_1);
        assertFalse(contenido.contains("Ana"), "el respaldo debe estar cifrado");
        assertFalse(contenido.contains("MOVIMIENTO"));
        assertFalse(contenido.contains("1500"));
    }

    @Test
    void dosRespaldosDeLosMismosDatosSonDistintosPorLaSalYElVector(@TempDir Path carpeta) throws IOException {
        Path a = carpeta.resolve("a.svb");
        Path b = carpeta.resolve("b.svb");
        origen.respaldo.generar(a, CLAVE);
        origen.respaldo.generar(b, CLAVE);
        assertFalse(java.util.Arrays.equals(Files.readAllBytes(a), Files.readAllBytes(b)));
    }

    @Test
    void generarUnRespaldoDejaConstanciaEnLaBitacora(@TempDir Path carpeta) {
        origen.respaldo.generar(carpeta.resolve("r.svb"), CLAVE);
        assertEquals("GENERACION", origen.bitacora.consultar().get(0).accion());
        assertEquals("RESPALDO", origen.bitacora.consultar().get(0).tipo());
    }

    @Test
    void exigeUnaContrasenaDeAlMenosCuatroCaracteres(@TempDir Path carpeta) {
        assertThrows(AppException.class, () -> origen.respaldo.generar(carpeta.resolve("r.svb"), "abc".toCharArray()));
        assertThrows(AppException.class, () -> origen.respaldo.generar(carpeta.resolve("r.svb"), null));
        assertFalse(Files.exists(carpeta.resolve("r.svb")));
    }

    // ---------- RF-RES-02 / SEG-03: restaurar en otra computadora ----------

    @Test
    void restauraTodosLosDatosEnUnaComputadoraNueva(@TempDir Path carpeta) {
        Path archivo = carpeta.resolve("respaldo.svb");
        origen.respaldo.generar(archivo, CLAVE);

        try (Equipo nuevo = new Equipo("nuevo")) {
            assertTrue(nuevo.movimientos.buscar(FiltroMovimiento.de(null)).isEmpty());

            nuevo.respaldo.restaurar(archivo, CLAVE);

            FiltroMovimiento todosConCancelados = new FiltroMovimiento(null, null, null, null, null, null, null, true);
            List<Movimiento> originales = origen.movimientos.buscar(todosConCancelados);
            List<Movimiento> restaurados = nuevo.movimientos.buscar(todosConCancelados);
            assertEquals(originales.size(), restaurados.size());
            for (int i = 0; i < originales.size(); i++) {
                Movimiento o = originales.get(i);
                Movimiento r = restaurados.get(i);
                assertEquals(o.tipo(), r.tipo());
                assertEquals(o.fecha(), r.fecha());
                assertEquals(o.monto(), r.monto());
                assertEquals(o.concepto(), r.concepto());
                assertEquals(o.categoria(), r.categoria());
                assertEquals(o.usuario(), r.usuario());
                assertEquals(o.formaPago(), r.formaPago());
                assertEquals(o.proveedor(), r.proveedor());
                assertEquals(o.cancelado(), r.cancelado());
            }
        }
    }

    @Test
    void restauraLosPendientesYSuVinculoConElMovimiento(@TempDir Path carpeta) {
        Path archivo = carpeta.resolve("respaldo.svb");
        origen.respaldo.generar(archivo, CLAVE);

        try (Equipo nuevo = new Equipo("nuevo")) {
            nuevo.respaldo.restaurar(archivo, CLAVE);

            List<Pendiente> restaurados = nuevo.pendientes.todos();
            assertEquals(2, restaurados.size());
            Pendiente sinPagar = restaurados.get(0);
            Pendiente pagado = restaurados.get(1);
            assertFalse(sinPagar.pagado());
            assertEquals("Carla Prueba", sinPagar.contraparte());
            assertTrue(pagado.pagado());
            assertTrue(pagado.movimientoId() != null);
            // el id del movimiento vinculado debe existir en la computadora nueva
            assertTrue(nuevo.movimientos.buscar(new FiltroMovimiento(TipoMovimiento.EGRESO, null, null, null, null,
                    null, null, false)).stream().anyMatch(m -> m.id() == pagado.movimientoId()));
        }
    }

    @Test
    void restauraLaBitacoraYAgregaLaConstanciaDeLaRestauracion(@TempDir Path carpeta) {
        origen.respaldo.generar(carpeta.resolve("r.svb"), CLAVE);
        int entradasDelOrigen = origen.bitacora.consultar().size();

        try (Equipo nuevo = new Equipo("nuevo")) {
            nuevo.respaldo.restaurar(carpeta.resolve("r.svb"), CLAVE);

            List<mx.tecmilenio.svcobranza.model.EntradaBitacora> entradas = nuevo.bitacora.consultar();
            assertEquals(entradasDelOrigen, entradas.size());
            assertEquals("RESTAURACION", entradas.get(0).accion());
            assertTrue(entradas.stream().anyMatch(e -> e.accion().equals("ALTA")));
        }
    }

    @Test
    void restaurarReemplazaLosDatosQueYaHabiaEnLaComputadora(@TempDir Path carpeta) {
        origen.respaldo.generar(carpeta.resolve("r.svb"), CLAVE);

        try (Equipo nuevo = new Equipo("nuevo")) {
            nuevo.movimientos.registrar(new Movimiento(0, TipoMovimiento.INGRESO, HOY, new BigDecimal("1"),
                    "Dato viejo que debe desaparecer", "Otros", "Viejo Prueba", FormaPago.EFECTIVO, null, false),
                    false);

            nuevo.respaldo.restaurar(carpeta.resolve("r.svb"), CLAVE);

            assertTrue(nuevo.movimientos.buscar(new FiltroMovimiento(null, null, "viejo", null, null, null, null,
                    true)).isEmpty());
        }
    }

    @Test
    void despuesDeRestaurarSePuedeSeguirTrabajando(@TempDir Path carpeta) {
        origen.respaldo.generar(carpeta.resolve("r.svb"), CLAVE);

        try (Equipo nuevo = new Equipo("nuevo")) {
            nuevo.respaldo.restaurar(carpeta.resolve("r.svb"), CLAVE);
            Movimiento m = nuevo.movimientos.registrar(new Movimiento(0, TipoMovimiento.INGRESO, HOY.plusDays(2),
                    new BigDecimal("77"), "Nuevo despues de restaurar", "Otros", "Dana Prueba", FormaPago.EFECTIVO,
                    null, false), false);
            assertTrue(m.id() > 0);
            // 3 del origen + el egreso generado al pagar el pendiente + el nuevo = 5
            assertEquals(5, nuevo.movimientos.buscar(new FiltroMovimiento(null, null, null, null, null, null, null,
                    true)).size());
        }
    }

    // ---------- SEG-03: proteccion ----------

    @Test
    void unaContrasenaIncorrectaNoRevelaNiBorraNada(@TempDir Path carpeta) {
        origen.respaldo.generar(carpeta.resolve("r.svb"), CLAVE);

        try (Equipo nuevo = new Equipo("nuevo")) {
            nuevo.movimientos.registrar(new Movimiento(0, TipoMovimiento.INGRESO, HOY, new BigDecimal("5"),
                    "Dato que debe conservarse", "Otros", "Eva Prueba", FormaPago.EFECTIVO, null, false), false);

            AppException e = assertThrows(AppException.class,
                    () -> nuevo.respaldo.restaurar(carpeta.resolve("r.svb"), "otra-clave".toCharArray()));

            assertTrue(e.getMessage().contains("contraseña"));
            assertEquals(1, nuevo.movimientos.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).size());
        }
    }

    @Test
    void unArchivoModificadoSeRechazaYNoBorraNada(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("r.svb");
        origen.respaldo.generar(archivo, CLAVE);
        byte[] bytes = Files.readAllBytes(archivo);
        bytes[bytes.length - 3] ^= 0x55;
        Files.write(archivo, bytes);

        try (Equipo nuevo = new Equipo("nuevo")) {
            nuevo.movimientos.registrar(new Movimiento(0, TipoMovimiento.INGRESO, HOY, new BigDecimal("5"),
                    "Dato que debe conservarse", "Otros", "Eva Prueba", FormaPago.EFECTIVO, null, false), false);

            assertThrows(AppException.class, () -> nuevo.respaldo.restaurar(archivo, CLAVE));

            assertEquals(1, nuevo.movimientos.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).size());
        }
    }

    @Test
    void unArchivoQueNoEsUnRespaldoSeRechazaConUnMensajeClaro(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("cualquiera.txt");
        Files.writeString(archivo, "esto no es un respaldo de nada, solo texto suelto para la prueba");
        AppException e = assertThrows(AppException.class, () -> origen.respaldo.restaurar(archivo, CLAVE));
        assertTrue(e.getMessage().contains("no es un respaldo"));
    }

    @Test
    void unArchivoInexistenteDaUnErrorClaro(@TempDir Path carpeta) {
        assertThrows(AppException.class, () -> origen.respaldo.restaurar(carpeta.resolve("no-existe.svb"), CLAVE));
    }

    // ---------- Csv y CifradoRespaldo ----------

    @Test
    void elCsvConservaComillasComasYSaltosDeLinea() {
        String linea = Csv.linea(List.of("a,b", "dice \"hola\"", "dos\nlineas", "", "normal"));
        List<List<String>> leido = Csv.leer(linea + Csv.linea(List.of("x", "y")));

        assertEquals(2, leido.size());
        assertEquals(List.of("a,b", "dice \"hola\"", "dos\nlineas", "", "normal"), leido.get(0));
        assertEquals(List.of("x", "y"), leido.get(1));
    }

    @Test
    void elCsvAceptaSaltosDeLineaDeWindows() {
        List<List<String>> leido = Csv.leer("\"a\",\"b\"\r\n\"c\",\"d\"\r\n");
        assertEquals(2, leido.size());
        assertEquals(List.of("c", "d"), leido.get(1));
    }

    @Test
    void elCifradoDevuelveLosMismosBytesConLaContrasenaCorrecta() {
        byte[] datos = "contenido de prueba con acentos: áéíóú ñ".getBytes(StandardCharsets.UTF_8);
        byte[] cifrado = CifradoRespaldo.cifrar(datos, CLAVE);
        assertNotEquals(datos.length, cifrado.length);
        assertArrayEquals(datos, CifradoRespaldo.descifrar(cifrado, CLAVE));
    }
}
