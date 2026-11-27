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
import java.util.Optional;

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

class CobroServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 24);
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

    /** Los servicios sobre una misma base de datos, con "hoy" a elegir (simula que pasa el tiempo). */
    private static final class Entorno {
        final UsuarioDao usuarioDao;
        final PendienteDao pendienteDao;
        final ExperienciaDao experienciaDao;
        final BitacoraService bitacora;
        final MovimientoService movimientos;
        final CobroService cobros;
        final UsuarioService usuarios;
        final ExperienciaService experiencia;

        Entorno(Database db, LocalDate hoy) {
            Clock reloj = Clock.fixed(hoy.atStartOfDay(ZONA).toInstant(), ZONA);
            usuarioDao = new UsuarioDao(db);
            MovimientoDao movimientoDao = new MovimientoDao(db);
            pendienteDao = new PendienteDao(db);
            experienciaDao = new ExperienciaDao(db);
            ConfiguracionDao configuracionDao = new ConfiguracionDao(db);
            bitacora = new BitacoraService(new BitacoraDao(db), reloj);
            movimientos = new MovimientoService(db, movimientoDao, new CategoriaDao(db), usuarioDao, bitacora);
            cobros = new CobroService(db, usuarioDao, pendienteDao, experienciaDao, configuracionDao, movimientos,
                    bitacora, reloj);
            usuarios = new UsuarioService(db, usuarioDao, movimientoDao, pendienteDao, cobros, bitacora, reloj);
            experiencia = new ExperienciaService(db, experienciaDao, cobros, bitacora, reloj);
        }
    }

    private Database db;
    private Entorno e;

    @BeforeEach
    void preparar() {
        db = Database.enMemoria("cobros" + System.nanoTime());
        e = new Entorno(db, HOY);
    }

    @AfterEach
    void cerrar() {
        db.close();
    }

    // ---------- constructores de usuarios ----------

    private static UsuarioAtendido semanal(String nombre, int diasDesdeHoy) {
        return new UsuarioAtendido(0, nombre, HOY, HOY, null, false, Frecuencia.SEMANAL, new BigDecimal("900"),
                HOY.plusDays(diasDesdeHoy), null, false, null, null, List.of());
    }

    private static UsuarioAtendido mensual(String nombre, Integer dia) {
        return new UsuarioAtendido(0, nombre, HOY, HOY, null, false, Frecuencia.MENSUAL, new BigDecimal("3500"),
                null, dia, false, null, new BigDecimal("100"), List.of());
    }

    private static UsuarioAtendido conServicios(String nombre, boolean experiencia, Servicio... servicios) {
        return new UsuarioAtendido(0, nombre, HOY, HOY, null, false, Frecuencia.MENSUAL, new BigDecimal("3500"), null,
                1, experiencia, experiencia ? new BigDecimal("600") : null, null, List.of(servicios));
    }

    private List<Pendiente> cobrosDe(UsuarioAtendido u) {
        return e.pendienteDao.porUsuario(u.id());
    }

    private static List<LocalDate> fechas(List<Pendiente> lista, String categoria) {
        return lista.stream().filter(p -> p.categoria().equals(categoria)).map(Pendiente::fechaLimite).toList();
    }

    private UsuarioAtendido registrarAna() {
        return e.usuarios.registrar(semanal("Ana Prueba", 2));
    }

    // ---------- generación: estancia ----------

    @Test
    void laEstanciaSemanalGeneraUnCobroCadaSieteDiasHastaTreintaDias() {
        UsuarioAtendido ana = registrarAna();

        List<Pendiente> cobros = cobrosDe(ana);

        assertEquals(List.of(LocalDate.of(2026, 11, 26), LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 17), LocalDate.of(2026, 12, 24)), fechas(cobros, "Pago semanal"));
        for (Pendiente p : cobros) {
            assertEquals(EstadoPendiente.PENDIENTE, p.estado());
            assertEquals(new BigDecimal("900.00"), p.monto());
            assertEquals("Ana Prueba", p.usuario());
            assertNull(p.movimientoId());
        }
    }

    @Test
    void laEstanciaSemanalConFechaBaseEnElPasadoSigueSuRitmoDesdeHoy() {
        UsuarioAtendido ana = e.usuarios.registrar(semanal("Ana Prueba", -10));

        assertEquals(List.of(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 12),
                LocalDate.of(2026, 12, 19)), fechas(cobrosDe(ana), "Pago semanal"));
    }

    @Test
    void laEstanciaSemanalConFechaBaseHoyIncluyeElCobroDeHoy() {
        UsuarioAtendido ana = e.usuarios.registrar(semanal("Ana Prueba", 0));

        assertEquals(List.of(HOY, HOY.plusDays(7), HOY.plusDays(14), HOY.plusDays(21), HOY.plusDays(28)),
                fechas(cobrosDe(ana), "Pago semanal"));
    }

    @Test
    void laEstanciaMensualSoloGeneraLasFechasDentroDeLosTreintaDias() {
        UsuarioAtendido dia15 = e.usuarios.registrar(mensual("Quince", 15));
        UsuarioAtendido dia24 = e.usuarios.registrar(mensual("Veinticuatro", 24));
        UsuarioAtendido sinDia = e.usuarios.registrar(mensual("Sin Dia", null));

        assertEquals(List.of(LocalDate.of(2026, 12, 15)), fechas(cobrosDe(dia15), "Pago mensual"));
        assertEquals(List.of(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 12, 24)),
                fechas(cobrosDe(dia24), "Pago mensual"));
        assertEquals(List.of(LocalDate.of(2026, 12, 1)), fechas(cobrosDe(sinDia), "Pago mensual"));
        assertEquals(new BigDecimal("3500.00"), cobrosDe(dia15).get(0).monto());
    }

    @Test
    void siElMesNoTieneElDiaElegidoSeCobraElUltimoDiaDelMes() {
        assertEquals(List.of(LocalDate.of(2027, 2, 28)),
                CobroService.mensuales(31, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1)));
        assertEquals(List.of(LocalDate.of(2028, 2, 29)),
                CobroService.mensuales(30, LocalDate.of(2028, 2, 1), LocalDate.of(2028, 3, 1)));

        UsuarioAtendido dia31 = e.usuarios.registrar(mensual("Treinta y Uno", 31));
        assertEquals(List.of(LocalDate.of(2026, 11, 30)), fechas(cobrosDe(dia31), "Pago mensual"));
    }

    // ---------- generación: becado, tiendita, servicios, experiencia ----------

    @Test
    void elBecadoNoGeneraCobros() {
        UsuarioAtendido becado = e.usuarios.registrar(new UsuarioAtendido(0, "Eva Prueba", HOY, HOY, null, true, null,
                null, null, null, false, null, new BigDecimal("80"), List.of()));

        assertTrue(cobrosDe(becado).isEmpty());
    }

    @Test
    void laTienditaNuncaGeneraCobros() {
        UsuarioAtendido ana = e.usuarios.registrar(mensual("Ana Prueba", 15));

        assertTrue(cobrosDe(ana).stream().noneMatch(p -> p.categoria().equals("Tiendita")));
        assertEquals(new BigDecimal("100.00"), e.usuarioDao.buscarPorId(ana.id()).orElseThrow().montoTiendita());
    }

    @Test
    void losServiciosGeneranCobrosSegunSuRecurrenciaYSinFechaDeInicioNoCobran() {
        UsuarioAtendido ana = e.usuarios.registrar(conServicios("Ana Prueba", false,
                new Servicio(TipoServicio.MEDICO, new BigDecimal("250"), HOY.plusDays(1), Frecuencia.SEMANAL),
                new Servicio(TipoServicio.PSICOLOGO, new BigDecimal("300"), LocalDate.of(2026, 12, 10),
                        Frecuencia.MENSUAL)));
        UsuarioAtendido beto = e.usuarios.registrar(conServicios("Beto Prueba", false,
                new Servicio(TipoServicio.MEDICO, new BigDecimal("250"), null, Frecuencia.SEMANAL)));

        List<Pendiente> cobrosAna = cobrosDe(ana);
        assertEquals(List.of(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 9),
                LocalDate.of(2026, 12, 16), LocalDate.of(2026, 12, 23)), fechas(cobrosAna, "Médico"));
        assertEquals(List.of(LocalDate.of(2026, 12, 10)), fechas(cobrosAna, "Psicólogo"));
        assertEquals(new BigDecimal("250.00"), cobrosAna.stream().filter(p -> p.categoria().equals("Médico"))
                .findFirst().orElseThrow().monto());
        assertEquals(List.of(LocalDate.of(2026, 12, 1)), fechas(cobrosAna, "Pago mensual"));
        assertTrue(fechas(cobrosDe(beto), "Médico").isEmpty());
    }

    @Test
    void laExperienciaSeCobraEnLasFechasDelCalendarioDentroDeLosTreintaDias() {
        e.experienciaDao.insertar(HOY.plusDays(3));
        e.experienciaDao.insertar(HOY.plusDays(31));

        UsuarioAtendido ana = e.usuarios.registrar(conServicios("Ana Prueba", true));
        UsuarioAtendido beto = e.usuarios.registrar(conServicios("Beto Prueba", false));

        assertEquals(List.of(LocalDate.of(2026, 11, 27)), fechas(cobrosDe(ana), "Experiencia"));
        assertEquals(new BigDecimal("600.00"), cobrosDe(ana).stream()
                .filter(p -> p.categoria().equals("Experiencia")).findFirst().orElseThrow().monto());
        assertTrue(fechas(cobrosDe(beto), "Experiencia").isEmpty());
    }

    @Test
    void sinCalendarioDeExperienciaNoSeCobraExperiencia() {
        UsuarioAtendido ana = e.usuarios.registrar(conServicios("Ana Prueba", true));

        assertTrue(fechas(cobrosDe(ana), "Experiencia").isEmpty());
        assertFalse(cobrosDe(ana).isEmpty(), "la estancia sí se cobra");
    }

    @Test
    void alCapturarYEditarElCalendarioSeReemplazanLosCobrosDeExperienciaPendientes() {
        UsuarioAtendido ana = e.usuarios.registrar(conServicios("Ana Prueba", true));

        e.experiencia.definirPrimeraFecha(HOY.plusDays(3));
        assertEquals(List.of(LocalDate.of(2026, 11, 27)), fechas(cobrosDe(ana), "Experiencia"));

        e.experiencia.editarFecha(HOY.plusDays(3), HOY.plusDays(5));
        assertEquals(List.of(LocalDate.of(2026, 11, 29)), fechas(cobrosDe(ana), "Experiencia"));
        assertEquals(List.of(LocalDate.of(2026, 12, 1)), fechas(cobrosDe(ana), "Pago mensual"));
    }

    @Test
    void lasFechasDeExperienciaAnterioresAHoyNoGeneranCobro() {
        UsuarioAtendido ana = e.usuarios.registrar(conServicios("Ana Prueba", true));
        e.experienciaDao.insertar(HOY.minusDays(5));
        e.experienciaDao.insertar(HOY.plusDays(3));

        e.cobros.reemplazarExperiencia();

        assertEquals(List.of(LocalDate.of(2026, 11, 27)), fechas(cobrosDe(ana), "Experiencia"));
    }

    @Test
    void alEditarElCalendarioLosCobrosDeExperienciaVencidosNoSeTocan() {
        UsuarioAtendido ana = e.usuarios.registrar(conServicios("Ana Prueba", true));
        e.experiencia.definirPrimeraFecha(HOY.plusDays(3)); // cobro del 27/11

        Entorno despues = new Entorno(db, HOY.plusDays(10)); // 04/12: el cobro del 27/11 ya venció
        despues.experiencia.editarFecha(LocalDate.of(2026, 11, 27), LocalDate.of(2026, 12, 7));

        List<Pendiente> experiencia = cobrosDe(ana).stream().filter(p -> p.categoria().equals("Experiencia"))
                .toList();
        assertEquals(List.of(LocalDate.of(2026, 11, 27), LocalDate.of(2026, 12, 7)),
                experiencia.stream().map(Pendiente::fechaLimite).toList());
        assertTrue(experiencia.get(0).vencido(HOY.plusDays(10)));
    }

    // ---------- generación: sin duplicados, app cerrada ----------

    @Test
    void generarVariasVecesNoDuplicaCobros() {
        UsuarioAtendido ana = registrarAna();

        e.cobros.generarTodos();
        e.cobros.generarTodos();
        e.cobros.generarPara(ana);

        assertEquals(5, cobrosDe(ana).size());
    }

    @Test
    void generarSinUsuariosNoHaceNada() {
        e.cobros.generarTodos();
        assertTrue(e.cobros.visibles().isEmpty());
    }

    @Test
    void losCobrosQueCayeronConLaAplicacionCerradaQuedanVencidosConSuFechaCalculada() {
        UsuarioAtendido ana = registrarAna(); // 5 cobros: del 26/11 al 24/12
        LocalDate hoyDespues = HOY.plusDays(60); // 23/01/2027
        Entorno despues = new Entorno(db, hoyDespues);

        despues.cobros.generarTodos();

        List<Pendiente> cobros = despues.pendienteDao.porUsuario(ana.id());
        assertEquals(13, cobros.size());
        assertEquals(LocalDate.of(2026, 11, 26), cobros.get(0).fechaLimite());
        assertEquals(LocalDate.of(2027, 2, 18), cobros.get(12).fechaLimite());
        assertEquals(9, cobros.stream().filter(p -> p.vencido(hoyDespues)).count());
        assertEquals(LocalDate.of(2027, 1, 21), cobros.get(8).fechaLimite());
    }

    @Test
    void losCobrosVisiblesIncluyenLosVencidosYLosDeLosProximosTreintaDias() {
        UsuarioAtendido ana = registrarAna();
        Entorno despues = new Entorno(db, HOY.plusDays(10)); // 04/12

        List<Pendiente> visibles = despues.cobros.visibles();

        assertEquals(5, visibles.size());
        assertEquals(2, visibles.stream().filter(p -> p.vencido(HOY.plusDays(10))).count());
        assertEquals(ana.id(), visibles.get(0).usuarioId());
    }

    // ---------- edición de un usuario ----------

    @Test
    void alEditarSeConservanVencidosPagadosYCondonadosYElMontoNuevoAplicaSoloAFuturos() {
        UsuarioAtendido ana = registrarAna(); // 26/11, 3/12, 10/12, 17/12, 24/12 a 900
        List<Pendiente> original = cobrosDe(ana);
        e.cobros.condonar(original.get(2).id(), "Familia sin recursos"); // 10/12
        e.cobros.pagar(original.get(3).id(), HOY, FormaPago.EFECTIVO, new BigDecimal("900")); // 17/12

        Entorno despues = new Entorno(db, HOY.plusDays(10)); // 04/12
        despues.usuarios.editar(new UsuarioAtendido(ana.id(), "Ana Prueba", HOY, HOY, null, false,
                Frecuencia.SEMANAL, new BigDecimal("1200"), HOY.plusDays(2), null, false, null, null, List.of()));

        List<Pendiente> cobros = despues.pendienteDao.porUsuario(ana.id());
        assertEquals(List.of(LocalDate.of(2026, 11, 26), LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 17), LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 31)),
                cobros.stream().map(Pendiente::fechaLimite).toList());
        // vencidos: conservan su monto original
        assertEquals(EstadoPendiente.PENDIENTE, cobros.get(0).estado());
        assertEquals(new BigDecimal("900.00"), cobros.get(0).monto());
        assertEquals(new BigDecimal("900.00"), cobros.get(1).monto());
        // condonado y pagado no se tocan
        assertEquals(EstadoPendiente.CONDONADO, cobros.get(2).estado());
        assertEquals("Familia sin recursos", cobros.get(2).motivo());
        assertEquals(EstadoPendiente.PAGADO, cobros.get(3).estado());
        // lo futuro se reemplaza con el monto nuevo
        assertEquals(EstadoPendiente.PENDIENTE, cobros.get(4).estado());
        assertEquals(new BigDecimal("1200.00"), cobros.get(4).monto());
        assertEquals(new BigDecimal("1200.00"), cobros.get(5).monto());
    }

    @Test
    void alCambiarLaFrecuenciaSeReemplazanLosPendientesFuturos() {
        UsuarioAtendido ana = registrarAna();

        e.usuarios.editar(new UsuarioAtendido(ana.id(), "Ana Prueba", HOY, HOY, null, false, Frecuencia.MENSUAL,
                new BigDecimal("3500"), null, 15, false, null, null, List.of()));

        List<Pendiente> cobros = cobrosDe(ana);
        assertEquals(1, cobros.size());
        assertEquals("Pago mensual", cobros.get(0).categoria());
        assertEquals(LocalDate.of(2026, 12, 15), cobros.get(0).fechaLimite());
        assertEquals(new BigDecimal("3500.00"), cobros.get(0).monto());
    }

    @Test
    void despuesDeEditarGenerarDeNuevoNoRevivePendientesBorrados() {
        UsuarioAtendido ana = registrarAna();
        e.usuarios.editar(new UsuarioAtendido(ana.id(), "Ana Prueba", HOY, HOY, null, false, Frecuencia.MENSUAL,
                new BigDecimal("3500"), null, 15, false, null, null, List.of()));

        e.cobros.generarTodos();

        assertEquals(List.of(LocalDate.of(2026, 12, 15)), fechas(cobrosDe(ana), "Pago mensual"));
        assertTrue(fechas(cobrosDe(ana), "Pago semanal").isEmpty());
    }

    // ---------- pagar ----------

    @Test
    void pagarCreaElIngresoALNombreDelUsuarioYMarcaElCobroComoPagado() {
        UsuarioAtendido ana = registrarAna();
        Pendiente cobro = cobrosDe(ana).get(0);

        Movimiento ingreso = e.cobros.pagar(cobro.id(), HOY, FormaPago.TRANSFERENCIA, new BigDecimal("900"));

        assertTrue(ingreso.id() > 0);
        assertEquals(TipoMovimiento.INGRESO, ingreso.tipo());
        assertEquals("Ana Prueba", ingreso.usuario());
        assertEquals("Pago semanal", ingreso.categoria());
        assertEquals(new BigDecimal("900.00"), ingreso.monto());
        assertEquals(FormaPago.TRANSFERENCIA, ingreso.formaPago());
        assertEquals(HOY, ingreso.fecha());
        assertTrue(ingreso.concepto().contains("Pago semanal"));
        Pendiente pagado = e.pendienteDao.buscarPorId(cobro.id()).orElseThrow();
        assertEquals(EstadoPendiente.PAGADO, pagado.estado());
        assertEquals(ingreso.id(), pagado.movimientoId());
        assertEquals(1, e.movimientos.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).size());
        assertEquals("COBRO", e.bitacora.consultar().get(0).tipo());
        assertEquals("PAGO", e.bitacora.consultar().get(0).accion());
    }

    @Test
    void elMontoYLaFechaDelPagoSePuedenEditar() {
        UsuarioAtendido ana = registrarAna();
        Pendiente cobro = cobrosDe(ana).get(0);

        Movimiento ingreso = e.cobros.pagar(cobro.id(), HOY.minusDays(3), FormaPago.EFECTIVO, new BigDecimal("800"));

        assertEquals(new BigDecimal("800.00"), ingreso.monto());
        assertEquals(HOY.minusDays(3), ingreso.fecha());
        assertEquals(new BigDecimal("900.00"), e.pendienteDao.buscarPorId(cobro.id()).orElseThrow().monto());
    }

    @Test
    void pagarExigeFechaFormaDePagoYMontoMayorACero() {
        UsuarioAtendido ana = registrarAna();
        long id = cobrosDe(ana).get(0).id();

        assertThrows(AppException.class, () -> e.cobros.pagar(id, null, FormaPago.EFECTIVO, new BigDecimal("900")));
        assertThrows(AppException.class, () -> e.cobros.pagar(id, HOY, null, new BigDecimal("900")));
        assertThrows(AppException.class, () -> e.cobros.pagar(id, HOY, FormaPago.EFECTIVO, BigDecimal.ZERO));
        assertThrows(AppException.class, () -> e.cobros.pagar(id, HOY, FormaPago.EFECTIVO, null));

        assertEquals(EstadoPendiente.PENDIENTE, e.pendienteDao.buscarPorId(id).orElseThrow().estado());
        assertTrue(e.movimientos.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).isEmpty());
    }

    @Test
    void unCobroNoSePuedePagarDosVecesNiSiYaNoExiste() {
        UsuarioAtendido ana = registrarAna();
        long id = cobrosDe(ana).get(0).id();
        e.cobros.pagar(id, HOY, FormaPago.EFECTIVO, new BigDecimal("900"));

        assertThrows(AppException.class, () -> e.cobros.pagar(id, HOY, FormaPago.EFECTIVO, new BigDecimal("900")));
        assertThrows(AppException.class, () -> e.cobros.pagar(9999, HOY, FormaPago.EFECTIVO, new BigDecimal("1")));
        assertEquals(1, e.movimientos.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).size());
    }

    // ---------- condonar y revertir ----------

    @Test
    void condonarNoGeneraIngresoYDejaConstanciaEnLaBitacoraConElMotivo() {
        UsuarioAtendido ana = registrarAna();
        Pendiente cobro = cobrosDe(ana).get(0);

        e.cobros.condonar(cobro.id(), "  Familia   sin recursos ");

        Pendiente condonado = e.pendienteDao.buscarPorId(cobro.id()).orElseThrow();
        assertEquals(EstadoPendiente.CONDONADO, condonado.estado());
        assertEquals("Familia sin recursos", condonado.motivo());
        assertNull(condonado.movimientoId());
        assertTrue(e.movimientos.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).isEmpty());
        assertEquals("CONDONACION", e.bitacora.consultar().get(0).accion());
        assertEquals(new BigDecimal("900.00"), e.bitacora.consultar().get(0).monto());
        assertTrue(e.bitacora.consultar().get(0).detalle().contains("Ana Prueba"));
        assertTrue(e.bitacora.consultar().get(0).detalle().contains("Familia sin recursos"));
    }

    @Test
    void elMotivoDeLaCondonacionEsOpcional() {
        UsuarioAtendido ana = registrarAna();
        long id = cobrosDe(ana).get(0).id();

        e.cobros.condonar(id, "   ");

        assertNull(e.pendienteDao.buscarPorId(id).orElseThrow().motivo());
        assertEquals(EstadoPendiente.CONDONADO, e.pendienteDao.buscarPorId(id).orElseThrow().estado());
    }

    @Test
    void unCobroCondonadoDejaDeSerVisibleYSePuedeRevertir() {
        UsuarioAtendido ana = registrarAna();
        long id = cobrosDe(ana).get(0).id();
        e.cobros.condonar(id, "Motivo");
        assertEquals(4, e.cobros.visibles().size());

        e.cobros.revertirCondonacion(id);

        Pendiente revertido = e.pendienteDao.buscarPorId(id).orElseThrow();
        assertEquals(EstadoPendiente.PENDIENTE, revertido.estado());
        assertNull(revertido.motivo());
        assertEquals(5, e.cobros.visibles().size());
        assertEquals("REVERSION", e.bitacora.consultar().get(0).accion());
    }

    @Test
    void soloSePuedeRevertirUnCobroCondonado() {
        UsuarioAtendido ana = registrarAna();
        long pendiente = cobrosDe(ana).get(0).id();
        long pagado = cobrosDe(ana).get(1).id();
        e.cobros.pagar(pagado, HOY, FormaPago.EFECTIVO, new BigDecimal("900"));

        assertThrows(AppException.class, () -> e.cobros.revertirCondonacion(pendiente));
        assertThrows(AppException.class, () -> e.cobros.revertirCondonacion(pagado));
        assertThrows(AppException.class, () -> e.cobros.revertirCondonacion(9999));
    }

    @Test
    void noSePuedeCondonarUnCobroYaPagadoOCondonado() {
        UsuarioAtendido ana = registrarAna();
        long pagado = cobrosDe(ana).get(0).id();
        long condonado = cobrosDe(ana).get(1).id();
        e.cobros.pagar(pagado, HOY, FormaPago.EFECTIVO, new BigDecimal("900"));
        e.cobros.condonar(condonado, null);

        assertThrows(AppException.class, () -> e.cobros.condonar(pagado, "x"));
        assertThrows(AppException.class, () -> e.cobros.condonar(condonado, "x"));
        assertThrows(AppException.class, () -> e.cobros.pagar(condonado, HOY, FormaPago.EFECTIVO,
                new BigDecimal("900")));
    }

    // ---------- ingreso capturado a mano que liquida un cobro ----------

    private static Movimiento ingreso(String usuario, String categoria, String monto) {
        return new Movimiento(0, TipoMovimiento.INGRESO, HOY, new BigDecimal(monto), "Pago a mano", categoria,
                usuario, FormaPago.EFECTIVO, false);
    }

    @Test
    void unIngresoDelMismoUsuarioCategoriaYMontoLiquidaElCobroMasAntiguo() {
        UsuarioAtendido ana = registrarAna();
        Pendiente masAntiguo = cobrosDe(ana).get(0);

        Optional<Pendiente> cobro = e.cobros.cobroQueLiquida(ingreso("ANA prueba", "Pago semanal", "900"));

        assertTrue(cobro.isPresent());
        assertEquals(masAntiguo.id(), cobro.get().id());
        assertEquals(LocalDate.of(2026, 11, 26), cobro.get().fechaLimite());
    }

    @Test
    void siCambiaElMontoLaCategoriaOElUsuarioNoSeSugiereNingunCobro() {
        registrarAna();
        e.usuarios.registrar(semanal("Beto Prueba", 2));
        e.usuarios.registrar(new UsuarioAtendido(0, "Eva Prueba", HOY, HOY, null, true, null, null, null, null,
                false, null, null, List.of()));

        assertTrue(e.cobros.cobroQueLiquida(ingreso("Ana Prueba", "Pago semanal", "899.99")).isEmpty());
        assertTrue(e.cobros.cobroQueLiquida(ingreso("Ana Prueba", "Pago mensual", "900")).isEmpty());
        assertTrue(e.cobros.cobroQueLiquida(ingreso("Eva Prueba", "Pago semanal", "900")).isEmpty());
        assertTrue(e.cobros.cobroQueLiquida(ingreso("Nadie", "Pago semanal", "900")).isEmpty());
        assertTrue(e.cobros.cobroQueLiquida(ingreso(null, "Otros", "900")).isEmpty());
        assertTrue(e.cobros.cobroQueLiquida(null).isEmpty());
        Movimiento egreso = new Movimiento(0, TipoMovimiento.EGRESO, HOY, new BigDecimal("900"), "", "Donación a usuario",
                "Ana Prueba", null, false);
        assertTrue(e.cobros.cobroQueLiquida(egreso).isEmpty());
        Movimiento cancelado = ingreso("Ana Prueba", "Pago semanal", "900").comoCancelado();
        assertTrue(e.cobros.cobroQueLiquida(cancelado).isEmpty());
    }

    @Test
    void liquidarMarcaElCobroComoPagadoConElIngresoExistenteYLaSugerenciaPasaAlSiguiente() {
        UsuarioAtendido ana = registrarAna();
        List<Pendiente> original = cobrosDe(ana);
        Movimiento capturado = e.movimientos.registrar(ingreso("Ana Prueba", "Pago semanal", "900"), false);
        Pendiente sugerido = e.cobros.cobroQueLiquida(capturado).orElseThrow();
        assertEquals(original.get(0).id(), sugerido.id());

        e.cobros.liquidar(sugerido.id(), capturado.id());

        Pendiente pagado = e.pendienteDao.buscarPorId(sugerido.id()).orElseThrow();
        assertEquals(EstadoPendiente.PAGADO, pagado.estado());
        assertEquals(capturado.id(), pagado.movimientoId());
        assertEquals(1, e.movimientos.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).size());
        assertEquals(original.get(1).id(), e.cobros.cobroQueLiquida(capturado).orElseThrow().id());
        assertThrows(AppException.class, () -> e.cobros.liquidar(sugerido.id(), capturado.id()));
    }

    @Test
    void unCobroCondonadoNoSeSugiereParaLiquidar() {
        UsuarioAtendido ana = registrarAna();
        List<Pendiente> original = cobrosDe(ana);
        e.cobros.condonar(original.get(0).id(), null);

        Pendiente sugerido = e.cobros.cobroQueLiquida(ingreso("Ana Prueba", "Pago semanal", "900")).orElseThrow();

        assertEquals(original.get(1).id(), sugerido.id());
    }
}
