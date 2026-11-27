package mx.tecmilenio.svcobranza.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.db.BitacoraDao;
import mx.tecmilenio.svcobranza.db.CategoriaDao;
import mx.tecmilenio.svcobranza.db.ConfiguracionDao;
import mx.tecmilenio.svcobranza.db.Database;
import mx.tecmilenio.svcobranza.db.ExperienciaDao;
import mx.tecmilenio.svcobranza.db.MovimientoDao;
import mx.tecmilenio.svcobranza.db.PendienteDao;
import mx.tecmilenio.svcobranza.db.UsuarioDao;
import mx.tecmilenio.svcobranza.model.EstadoPendiente;
import mx.tecmilenio.svcobranza.model.FiltroMovimiento;
import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Frecuencia;
import mx.tecmilenio.svcobranza.model.Movimiento;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.Servicio;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;
import mx.tecmilenio.svcobranza.model.TipoServicio;
import mx.tecmilenio.svcobranza.model.UsuarioAtendido;

class UsuarioServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 24);

    private Database db;
    private UsuarioService servicio;
    private MovimientoService movimientos;
    private PendienteDao pendienteDao;
    private BitacoraService bitacora;

    @BeforeEach
    void preparar() {
        db = Database.enMemoria("usuarios" + System.nanoTime());
        ZoneId zona = ZoneId.of("America/Mexico_City");
        Clock reloj = Clock.fixed(HOY.atStartOfDay(zona).toInstant(), zona);
        bitacora = new BitacoraService(new BitacoraDao(db), reloj);
        UsuarioDao usuarioDao = new UsuarioDao(db);
        MovimientoDao movimientoDao = new MovimientoDao(db);
        pendienteDao = new PendienteDao(db);
        movimientos = new MovimientoService(db, movimientoDao, new CategoriaDao(db), usuarioDao, bitacora);
        CobroService cobros = new CobroService(db, usuarioDao, pendienteDao, new ExperienciaDao(db),
                new ConfiguracionDao(db), movimientos, bitacora, reloj);
        servicio = new UsuarioService(db, usuarioDao, movimientoDao, pendienteDao, cobros, bitacora, reloj);
    }

    @AfterEach
    void cerrar() {
        db.close();
    }

    private static UsuarioAtendido mensual(String nombre) {
        return new UsuarioAtendido(0, nombre, HOY.minusDays(10), HOY.minusYears(1), "Esquema A", false,
                Frecuencia.MENSUAL, new BigDecimal("3500"), null, 15, false, null, new BigDecimal("100"),
                List.of());
    }

    private static UsuarioAtendido semanal(String nombre) {
        return new UsuarioAtendido(0, nombre, HOY, HOY, null, false, Frecuencia.SEMANAL, new BigDecimal("900"),
                HOY.plusDays(2), null, false, null, null, List.of());
    }

    private static UsuarioAtendido becado(String nombre) {
        return new UsuarioAtendido(0, nombre, HOY, HOY, null, true, Frecuencia.MENSUAL, new BigDecimal("3500"),
                HOY, 5, true, new BigDecimal("600"), new BigDecimal("80"),
                List.of(new Servicio(TipoServicio.MEDICO, new BigDecimal("250"), HOY, Frecuencia.SEMANAL)));
    }

    private void assertRechaza(UsuarioAtendido u, String fragmentoDelMensaje) {
        AppException e = assertThrows(AppException.class, () -> servicio.registrar(u));
        assertTrue(e.getMessage().contains(fragmentoDelMensaje), e.getMessage());
        assertTrue(servicio.listar().isEmpty(), "no debe guardar nada si hay errores");
    }

    // ---------- alta ----------

    @Test
    void registraUnUsuarioMensualConSusDatos() {
        UsuarioAtendido guardado = servicio.registrar(mensual("Ana Prueba"));

        assertTrue(guardado.id() > 0);
        UsuarioAtendido leido = servicio.buscarPorId(guardado.id()).orElseThrow();
        assertEquals("Ana Prueba", leido.nombre());
        assertEquals(HOY.minusDays(10), leido.fechaIngreso());
        assertEquals("Esquema A", leido.esquema());
        assertEquals(Frecuencia.MENSUAL, leido.frecuenciaEstancia());
        assertEquals(new BigDecimal("3500.00"), leido.montoEstancia());
        assertEquals(15, leido.diaMes());
        assertNull(leido.fechaBase());
        assertEquals(new BigDecimal("100.00"), leido.montoTiendita());
    }

    @Test
    void laFechaDeRegistroEsLaDeHoyAunqueSeIndiqueOtra() {
        UsuarioAtendido guardado = servicio.registrar(mensual("Ana Prueba"));
        assertEquals(HOY, guardado.fechaRegistro());
        assertEquals(HOY, servicio.buscarPorId(guardado.id()).orElseThrow().fechaRegistro());
    }

    @Test
    void registraUnUsuarioSemanalConSuFechaBase() {
        UsuarioAtendido leido = servicio.buscarPorId(servicio.registrar(semanal("Beto Prueba")).id()).orElseThrow();
        assertEquals(Frecuencia.SEMANAL, leido.frecuenciaEstancia());
        assertEquals(HOY.plusDays(2), leido.fechaBase());
        assertNull(leido.diaMes());
    }

    @Test
    void laEstanciaMensualSinDiaUsaElDiaUno() {
        UsuarioAtendido sinDia = new UsuarioAtendido(0, "Carla Prueba", HOY, HOY, null, false, Frecuencia.MENSUAL,
                new BigDecimal("3000"), null, null, false, null, null, List.of());

        UsuarioAtendido leido = servicio.buscarPorId(servicio.registrar(sinDia).id()).orElseThrow();

        assertEquals(UsuarioService.DIA_MES_POR_DEFECTO, leido.diaMes());
        assertEquals(1, leido.diaMes());
    }

    @Test
    void laEstanciaSemanalIgnoraElDiaDelMesYLaMensualIgnoraLaFechaBase() {
        UsuarioAtendido semanalConDia = new UsuarioAtendido(0, "Uno Prueba", HOY, HOY, null, false,
                Frecuencia.SEMANAL, new BigDecimal("900"), HOY, 20, false, null, null, List.of());
        UsuarioAtendido mensualConBase = new UsuarioAtendido(0, "Dos Prueba", HOY, HOY, null, false,
                Frecuencia.MENSUAL, new BigDecimal("900"), HOY, 20, false, null, null, List.of());

        assertNull(servicio.registrar(semanalConDia).diaMes());
        assertNull(servicio.registrar(mensualConBase).fechaBase());
    }

    @Test
    void registraExperienciaYServiciosConSusMontos() {
        UsuarioAtendido completo = new UsuarioAtendido(0, "Dana Prueba", HOY, HOY, null, false, Frecuencia.MENSUAL,
                new BigDecimal("3500"), null, 1, true, new BigDecimal("600"), null,
                List.of(new Servicio(TipoServicio.MEDICO, new BigDecimal("250"), HOY.plusDays(1), Frecuencia.SEMANAL),
                        new Servicio(TipoServicio.PSICOLOGO, new BigDecimal("300"), null, Frecuencia.MENSUAL)));

        UsuarioAtendido leido = servicio.buscarPorId(servicio.registrar(completo).id()).orElseThrow();

        assertTrue(leido.experiencia());
        assertEquals(new BigDecimal("600.00"), leido.montoExperiencia());
        assertEquals(2, leido.servicios().size());
        assertEquals(new BigDecimal("250.00"), leido.servicio(TipoServicio.MEDICO).monto());
        assertEquals(HOY.plusDays(1), leido.servicio(TipoServicio.MEDICO).fechaInicio());
        assertNull(leido.servicio(TipoServicio.PSICOLOGO).fechaInicio());
    }

    @Test
    void limpiaLosEspaciosDelNombre() {
        UsuarioAtendido guardado = servicio.registrar(mensual("  Ana    Prueba  "));
        assertEquals("Ana Prueba", guardado.nombre());
    }

    @Test
    void elAltaQuedaEnLaBitacora() {
        servicio.registrar(mensual("Ana Prueba"));
        assertEquals("USUARIO", bitacora.consultar().get(0).tipo());
        assertEquals("ALTA", bitacora.consultar().get(0).accion());
        assertTrue(bitacora.consultar().get(0).detalle().contains("Ana Prueba"));
    }

    // ---------- becado ----------

    @Test
    void elBecadoSoloConservaElMontoDeTiendita() {
        UsuarioAtendido leido = servicio.buscarPorId(servicio.registrar(becado("Eva Prueba")).id()).orElseThrow();

        assertTrue(leido.becado());
        assertNull(leido.frecuenciaEstancia());
        assertNull(leido.montoEstancia());
        assertNull(leido.fechaBase());
        assertNull(leido.diaMes());
        assertFalse(leido.experiencia());
        assertNull(leido.montoExperiencia());
        assertTrue(leido.servicios().isEmpty());
        assertEquals(new BigDecimal("80.00"), leido.montoTiendita());
    }

    @Test
    void elBecadoNoNecesitaEstanciaNiMontos() {
        UsuarioAtendido minimo = new UsuarioAtendido(0, "Eva Prueba", HOY, HOY, null, true, null, null, null, null,
                false, null, null, List.of());
        assertTrue(servicio.registrar(minimo).id() > 0);
    }

    // ---------- validaciones ----------

    @Test
    void exigeElNombre() {
        assertRechaza(mensual("   "), "nombre");
    }

    @Test
    void exigeLaFechaDeIngreso() {
        UsuarioAtendido u = mensual("Ana Prueba");
        assertRechaza(new UsuarioAtendido(0, u.nombre(), null, HOY, null, false, Frecuencia.MENSUAL, u.montoEstancia(),
                null, 1, false, null, null, List.of()), "fecha de ingreso");
    }

    @Test
    void noSeRegistranUsuariosConFechaDeIngresoFutura() {
        UsuarioAtendido u = mensual("Ana Prueba");
        assertRechaza(new UsuarioAtendido(0, u.nombre(), HOY.plusDays(1), HOY, null, false, Frecuencia.MENSUAL,
                u.montoEstancia(), null, 1, false, null, null, List.of()), "posterior a hoy");
        // hoy sí es válido
        assertTrue(servicio.registrar(semanal("Beto Prueba")).id() > 0);
    }

    @Test
    void editarTampocoPermiteUnaFechaDeIngresoFutura() {
        UsuarioAtendido guardado = servicio.registrar(mensual("Ana Prueba"));
        UsuarioAtendido cambios = new UsuarioAtendido(guardado.id(), "Ana Prueba", HOY.plusDays(3), HOY, null, false,
                Frecuencia.MENSUAL, new BigDecimal("3500"), null, 15, false, null, null, List.of());

        AppException e = assertThrows(AppException.class, () -> servicio.editar(cambios));

        assertTrue(e.getMessage().contains("posterior a hoy"));
        assertEquals(HOY.minusDays(10), servicio.buscarPorId(guardado.id()).orElseThrow().fechaIngreso());
    }

    @Test
    void exigeFrecuenciaDeEstanciaSiNoEsBecado() {
        assertRechaza(new UsuarioAtendido(0, "Ana Prueba", HOY, HOY, null, false, null, new BigDecimal("100"), null,
                null, false, null, null, List.of()), "semanal o mensual");
    }

    @Test
    void exigeUnMontoDeEstanciaMayorACero() {
        assertRechaza(new UsuarioAtendido(0, "Ana Prueba", HOY, HOY, null, false, Frecuencia.MENSUAL, BigDecimal.ZERO,
                null, 1, false, null, null, List.of()), "estancia");
        assertRechaza(new UsuarioAtendido(0, "Ana Prueba", HOY, HOY, null, false, Frecuencia.MENSUAL, null, null, 1,
                false, null, null, List.of()), "estancia");
    }

    @Test
    void laEstanciaSemanalExigeFechaBase() {
        assertRechaza(new UsuarioAtendido(0, "Ana Prueba", HOY, HOY, null, false, Frecuencia.SEMANAL,
                new BigDecimal("900"), null, null, false, null, null, List.of()), "cobro semanal");
    }

    @Test
    void elDiaDelMesDebeEstarEntre1Y31() {
        for (int dia : new int[] { 0, 32, -3 }) {
            assertRechaza(new UsuarioAtendido(0, "Ana Prueba", HOY, HOY, null, false, Frecuencia.MENSUAL,
                    new BigDecimal("900"), null, dia, false, null, null, List.of()), "día del mes");
        }
        assertTrue(servicio.registrar(new UsuarioAtendido(0, "Dia Treinta y Uno", HOY, HOY, null, false,
                Frecuencia.MENSUAL, new BigDecimal("900"), null, 31, false, null, null, List.of())).id() > 0);
    }

    @Test
    void laExperienciaExigeUnMonto() {
        assertRechaza(new UsuarioAtendido(0, "Ana Prueba", HOY, HOY, null, false, Frecuencia.MENSUAL,
                new BigDecimal("900"), null, 1, true, null, null, List.of()), "experiencia");
    }

    @Test
    void rechazaServiciosRepetidosOSinMonto() {
        Servicio medico = new Servicio(TipoServicio.MEDICO, new BigDecimal("250"), HOY, Frecuencia.SEMANAL);
        assertRechaza(new UsuarioAtendido(0, "Ana Prueba", HOY, HOY, null, false, Frecuencia.MENSUAL,
                new BigDecimal("900"), null, 1, false, null, null, List.of(medico, medico)), "repetido");
        assertRechaza(new UsuarioAtendido(0, "Ana Prueba", HOY, HOY, null, false, Frecuencia.MENSUAL,
                new BigDecimal("900"), null, 1, false, null, null,
                List.of(new Servicio(TipoServicio.PSICOLOGO, BigDecimal.ZERO, HOY, Frecuencia.MENSUAL))), "monto");
        assertRechaza(new UsuarioAtendido(0, "Ana Prueba", HOY, HOY, null, false, Frecuencia.MENSUAL,
                new BigDecimal("900"), null, 1, false, null, null,
                List.of(new Servicio(TipoServicio.PSICOLOGO, new BigDecimal("10"), HOY, null))), "semanal o mensual");
    }

    @Test
    void laTienditaNoPuedeSerNegativa() {
        assertRechaza(new UsuarioAtendido(0, "Ana Prueba", HOY, HOY, null, true, null, null, null, null, false, null,
                new BigDecimal("-1"), List.of()), "tiendita");
    }

    @Test
    void noPermiteDosUsuariosConElMismoNombreSinImportarMayusculas() {
        servicio.registrar(mensual("Ana Prueba"));

        AppException e = assertThrows(AppException.class, () -> servicio.registrar(mensual("  ana   PRUEBA ")));

        assertTrue(e.getMessage().contains("Ya existe"));
        assertEquals(1, servicio.listar().size());
    }

    // ---------- edicion ----------

    @Test
    void editaLosDatosYConservaLaFechaDeRegistro() {
        UsuarioAtendido guardado = servicio.registrar(mensual("Ana Prueba"));
        UsuarioAtendido cambios = new UsuarioAtendido(guardado.id(), "Ana Prueba", HOY.minusDays(10), HOY.plusDays(40),
                "Esquema B", false, Frecuencia.SEMANAL, new BigDecimal("1200"), HOY, null, true,
                new BigDecimal("650"), null, List.of());

        servicio.editar(cambios);

        UsuarioAtendido leido = servicio.buscarPorId(guardado.id()).orElseThrow();
        assertEquals(HOY, leido.fechaRegistro());
        assertEquals("Esquema B", leido.esquema());
        assertEquals(Frecuencia.SEMANAL, leido.frecuenciaEstancia());
        assertEquals(new BigDecimal("1200.00"), leido.montoEstancia());
        assertNull(leido.diaMes());
        assertTrue(leido.experiencia());
        assertEquals("EDICION", bitacora.consultar().get(0).accion());
    }

    @Test
    void alEditarSePuedenQuitarLosServicios() {
        UsuarioAtendido conServicio = new UsuarioAtendido(0, "Ana Prueba", HOY, HOY, null, false,
                Frecuencia.MENSUAL, new BigDecimal("900"), null, 1, false, null, null,
                List.of(new Servicio(TipoServicio.MEDICO, new BigDecimal("250"), HOY, Frecuencia.SEMANAL)));
        UsuarioAtendido guardado = servicio.registrar(conServicio);

        servicio.editar(new UsuarioAtendido(guardado.id(), "Ana Prueba", HOY, HOY, null, false, Frecuencia.MENSUAL,
                new BigDecimal("900"), null, 1, false, null, null, List.of()));

        assertTrue(servicio.buscarPorId(guardado.id()).orElseThrow().servicios().isEmpty());
    }

    @Test
    void alCambiarElNombreElHistorialDeIngresosSeConserva() {
        UsuarioAtendido guardado = servicio.registrar(mensual("Ana Prueba"));
        movimientos.registrar(new Movimiento(0, TipoMovimiento.INGRESO, HOY, new BigDecimal("500"), "Tiendita",
                "Tiendita", "Ana Prueba", FormaPago.EFECTIVO, false), false);

        servicio.editar(new UsuarioAtendido(guardado.id(), "Ana Nueva", HOY, HOY, null, false, Frecuencia.MENSUAL,
                new BigDecimal("3500"), null, 15, false, null, null, List.of()));

        List<Movimiento> ingresos = movimientos.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO));
        assertEquals(1, ingresos.size());
        assertEquals("Ana Nueva", ingresos.get(0).usuario());
    }

    @Test
    void editarNoPermiteTomarElNombreDeOtroUsuarioPeroSiConservarElPropio() {
        servicio.registrar(mensual("Ana Prueba"));
        UsuarioAtendido beto = servicio.registrar(mensual("Beto Prueba"));

        assertThrows(AppException.class, () -> servicio.editar(mensual("ANA PRUEBA").conId(beto.id())));
        servicio.editar(mensual("BETO PRUEBA").conId(beto.id()));

        assertEquals("BETO PRUEBA", servicio.buscarPorId(beto.id()).orElseThrow().nombre());
    }

    @Test
    void editarUnUsuarioInexistenteDaUnErrorClaro() {
        assertThrows(AppException.class, () -> servicio.editar(mensual("Fantasma").conId(999)));
    }

    // ---------- baja ----------

    @Test
    void borrarEliminaAlUsuarioYSusCobrosPendientesPeroConservaSusIngresos() {
        // Al registrarla se genera sola su cobro mensual del 15/12 (Pendiente)
        UsuarioAtendido guardado = servicio.registrar(mensual("Ana Prueba"));
        Movimiento pago = movimientos.registrar(new Movimiento(0, TipoMovimiento.INGRESO, HOY, new BigDecimal("3500"),
                "Pago mensual", "Pago mensual", "Ana Prueba", FormaPago.EFECTIVO, false), false);
        assertEquals(1, pendienteDao.porUsuario(guardado.id()).size());
        pendienteDao.insertar(new Pendiente(0, guardado.id(), "Ana Prueba", "Pago mensual", HOY,
                new BigDecimal("3500"), EstadoPendiente.PAGADO, null, pago.id()));

        servicio.borrar(guardado.id());

        assertTrue(servicio.buscarPorId(guardado.id()).isEmpty());
        assertTrue(servicio.listar().isEmpty());
        assertTrue(pendienteDao.listarTodos().isEmpty());
        List<Movimiento> ingresos = movimientos.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO));
        assertEquals(1, ingresos.size());
        assertEquals("Ana Prueba", ingresos.get(0).usuario());
        assertEquals("BAJA", bitacora.consultar().get(0).accion());
        assertTrue(bitacora.consultar().get(0).detalle().contains("1 cobros pendientes"));
    }

    @Test
    void borrarUnUsuarioInexistenteDaUnErrorClaro() {
        assertThrows(AppException.class, () -> servicio.borrar(999));
    }

    // ---------- consultas ----------

    @Test
    void listaLosUsuariosOrdenadosPorNombre() {
        servicio.registrar(mensual("Carla Prueba"));
        servicio.registrar(mensual("ana Prueba"));
        servicio.registrar(mensual("Beto Prueba"));

        assertEquals(List.of("ana Prueba", "Beto Prueba", "Carla Prueba"),
                servicio.listar().stream().map(UsuarioAtendido::nombre).toList());
    }

    @Test
    void buscaPorNombreSinDistinguirMayusculasYIgnoraNombresVacios() {
        servicio.registrar(mensual("Ana Prueba"));

        assertTrue(servicio.buscarPorNombre("ANA prueba").isPresent());
        assertTrue(servicio.buscarPorNombre("Nadie").isEmpty());
        assertTrue(servicio.buscarPorNombre(null).isEmpty());
        assertTrue(servicio.buscarPorNombre("  ").isEmpty());
    }

    @Test
    void cobrosDeDevuelveTodosLosEstadosDelUsuario() {
        // Los becados no generan cobros solos, así que solo cuentan los que se insertan aquí
        UsuarioAtendido ana = servicio.registrar(becado("Ana Prueba"));
        UsuarioAtendido beto = servicio.registrar(becado("Beto Prueba"));
        pendienteDao.insertar(new Pendiente(0, ana.id(), "Ana Prueba", "Pago mensual", HOY,
                new BigDecimal("3500"), EstadoPendiente.PENDIENTE, null, null));
        pendienteDao.insertar(new Pendiente(0, ana.id(), "Ana Prueba", "Médico", HOY,
                new BigDecimal("250"), EstadoPendiente.CONDONADO, "Sin recursos", null));
        pendienteDao.insertar(new Pendiente(0, beto.id(), "Beto Prueba", "Pago mensual", HOY,
                new BigDecimal("3500"), EstadoPendiente.PENDIENTE, null, null));

        assertEquals(2, servicio.cobrosDe(ana.id()).size());
        assertEquals(1, servicio.cobrosDe(beto.id()).size());
    }
}
