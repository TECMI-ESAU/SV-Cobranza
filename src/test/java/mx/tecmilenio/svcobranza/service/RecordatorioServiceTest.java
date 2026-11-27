package mx.tecmilenio.svcobranza.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import mx.tecmilenio.svcobranza.model.ColorAviso;
import mx.tecmilenio.svcobranza.model.EstadoPendiente;
import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Frecuencia;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.Recordatorio;
import mx.tecmilenio.svcobranza.model.Servicio;
import mx.tecmilenio.svcobranza.model.TipoServicio;
import mx.tecmilenio.svcobranza.model.UsuarioAtendido;

class RecordatorioServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 24);
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

    private Database db;
    private UsuarioDao usuarioDao;
    private PendienteDao pendienteDao;
    private ConfiguracionDao configuracion;
    private CobroService cobros;
    private RecordatorioService servicio;
    private long ana;

    @BeforeEach
    void preparar() {
        db = Database.enMemoria("recordatorios" + System.nanoTime());
        Clock reloj = Clock.fixed(HOY.atStartOfDay(ZONA).toInstant(), ZONA);
        usuarioDao = new UsuarioDao(db);
        pendienteDao = new PendienteDao(db);
        configuracion = new ConfiguracionDao(db);
        BitacoraService bitacora = new BitacoraService(new BitacoraDao(db), reloj);
        MovimientoService movimientos = new MovimientoService(db, new MovimientoDao(db), new CategoriaDao(db),
                usuarioDao, bitacora);
        cobros = new CobroService(db, usuarioDao, pendienteDao, new ExperienciaDao(db), configuracion, movimientos,
                bitacora, reloj);
        servicio = new RecordatorioService(usuarioDao, pendienteDao, configuracion, reloj);
        // Ana no genera cobros solos (becada): cada prueba inserta los que necesita
        ana = usuarioDao.insertar(new UsuarioAtendido(0, "Ana Prueba", HOY, HOY, null, true, null, null, null, null,
                false, null, null, List.of(new Servicio(TipoServicio.MEDICO, new BigDecimal("250"), HOY,
                        Frecuencia.SEMANAL),
                        new Servicio(TipoServicio.PSICOLOGO, new BigDecimal("300"), HOY, Frecuencia.MENSUAL))));
    }

    @AfterEach
    void cerrar() {
        db.close();
    }

    private long cobro(String categoria, int diasDesdeHoy) {
        return pendienteDao.insertar(new Pendiente(0, ana, "Ana Prueba", categoria, HOY.plusDays(diasDesdeHoy),
                new BigDecimal("100"), EstadoPendiente.PENDIENTE, null, null));
    }

    private List<Recordatorio> avisos() {
        return servicio.recordatorios();
    }

    private static void assertUno(List<Recordatorio> lista, ColorAviso color, long dias) {
        assertEquals(1, lista.size());
        assertEquals(color, lista.get(0).color());
        assertEquals(dias, lista.get(0).dias());
    }

    // ---------- Experiencia: 7 días antes ----------

    @Test
    void experienciaAvisaDesdeSieteDiasAntesEnDorado() {
        cobro("Experiencia", 8);
        assertTrue(avisos().isEmpty());

        pendienteDao.borrarTodo();
        cobro("Experiencia", 7);
        assertUno(avisos(), ColorAviso.DORADO, 7);

        pendienteDao.borrarTodo();
        cobro("Experiencia", 0);
        assertUno(avisos(), ColorAviso.DORADO, 0);
    }

    // ---------- semanal: el día de su fecha ----------

    @Test
    void pagoSemanalAvisaElDiaDeSuFechaEnAzulYNoAntes() {
        cobro("Pago semanal", 1);
        assertTrue(avisos().isEmpty());

        pendienteDao.borrarTodo();
        cobro("Pago semanal", 0);
        assertUno(avisos(), ColorAviso.AZUL, 0);
    }

    // ---------- mensual: 3 días antes ----------

    @Test
    void pagoMensualAvisaDesdeTresDiasAntesEnVerde() {
        cobro("Pago mensual", 4);
        assertTrue(avisos().isEmpty());

        pendienteDao.borrarTodo();
        cobro("Pago mensual", 3);
        assertUno(avisos(), ColorAviso.VERDE, 3);

        pendienteDao.borrarTodo();
        cobro("Pago mensual", 0);
        assertUno(avisos(), ColorAviso.VERDE, 0);
    }

    // ---------- Médico y Psicólogo siguen la regla de su recurrencia ----------

    @Test
    void medicoSemanalAvisaElDiaDeSuFechaEnMorado() {
        cobro("Médico", 1);
        assertTrue(avisos().isEmpty());

        pendienteDao.borrarTodo();
        cobro("Médico", 0);
        assertUno(avisos(), ColorAviso.MORADO, 0);
    }

    @Test
    void psicologoMensualAvisaTresDiasAntesEnMoradoClaro() {
        cobro("Psicólogo", 4);
        assertTrue(avisos().isEmpty());

        pendienteDao.borrarTodo();
        cobro("Psicólogo", 3);
        assertUno(avisos(), ColorAviso.MORADO_CLARO, 3);
    }

    @Test
    void siElServicioCambiaDeRecurrenciaSeUsaLaVigente() {
        UsuarioAtendido usuario = usuarioDao.buscarPorId(ana).orElseThrow();
        usuarioDao.actualizar(new UsuarioAtendido(ana, usuario.nombre(), usuario.fechaIngreso(),
                usuario.fechaRegistro(), null, true, null, null, null, null, false, null, null,
                List.of(new Servicio(TipoServicio.MEDICO, new BigDecimal("250"), HOY, Frecuencia.MENSUAL))));

        cobro("Médico", 3);

        assertUno(avisos(), ColorAviso.MORADO, 3);
    }

    // ---------- vencidos ----------

    @Test
    void cualquierCobroVencidoAvisaEnRojoHastaPagarseOCondonarse() {
        cobro("Pago semanal", -1);
        cobro("Experiencia", -20);
        cobro("Médico", -2);
        cobro("Psicólogo", -3);
        cobro("Pago mensual", -40);

        List<Recordatorio> lista = avisos();

        assertEquals(5, lista.size());
        assertTrue(lista.stream().allMatch(r -> r.color() == ColorAviso.ROJO && r.vencido()));
        assertEquals(-40, lista.get(0).dias());
    }

    @Test
    void unCobroDeHoyNoEstaVencidoPeroUnoDeAyerSi() {
        cobro("Pago semanal", 0);
        cobro("Pago semanal", -1);

        List<Recordatorio> lista = avisos();

        assertEquals(2, lista.size());
        assertEquals(ColorAviso.ROJO, lista.get(0).color());
        assertEquals(ColorAviso.AZUL, lista.get(1).color());
    }

    @Test
    void losPagadosYCondonadosNuncaAvisan() {
        long pagado = cobro("Pago semanal", -5);
        long condonado = cobro("Experiencia", -9);
        cobro("Pago mensual", 1);
        cobros.pagar(pagado, HOY, FormaPago.EFECTIVO, new BigDecimal("100"));
        cobros.condonar(condonado, "Motivo");

        List<Recordatorio> lista = avisos();

        assertEquals(1, lista.size());
        assertEquals("Pago mensual", lista.get(0).cobro().categoria());
    }

    @Test
    void alCondonarseUnCobroVencidoDejaDeAvisar() {
        long id = cobro("Pago semanal", -3);
        assertEquals(1, avisos().size());

        cobros.condonar(id, null);

        assertTrue(avisos().isEmpty());
    }

    @Test
    void losVencidosVanPrimeroYLuegoPorFecha() {
        cobro("Pago mensual", 2);
        cobro("Pago semanal", 0);
        cobro("Pago semanal", -1);
        cobro("Experiencia", 5);

        List<String> orden = avisos().stream().map(r -> r.cobro().categoria() + r.dias()).toList();

        assertEquals(List.of("Pago semanal-1", "Pago semanal0", "Pago mensual2", "Experiencia5"), orden);
    }

    @Test
    void sinCobrosNoHayRecordatorios() {
        assertTrue(avisos().isEmpty());
    }

    // ---------- días de aviso configurables ----------

    @Test
    void losDiasDeAvisoPorDefectoSonSieteYTres() {
        assertEquals(7, servicio.diasAvisoExperiencia());
        assertEquals(3, servicio.diasAvisoMensual());
    }

    @Test
    void losDiasDeAvisoSePuedenCambiarYSeAplicanAlMomento() {
        cobro("Experiencia", 10);
        cobro("Pago mensual", 5);
        assertTrue(avisos().isEmpty());

        servicio.definirDiasAviso(10, 5);

        assertEquals(10, servicio.diasAvisoExperiencia());
        assertEquals(5, servicio.diasAvisoMensual());
        assertEquals(2, avisos().size());
    }

    @Test
    void losDiasDeAvisoDebenEstarEntreCeroYTreinta() {
        assertThrows(AppException.class, () -> servicio.definirDiasAviso(-1, 3));
        assertThrows(AppException.class, () -> servicio.definirDiasAviso(7, 31));
        servicio.definirDiasAviso(0, 30);
        assertEquals(0, servicio.diasAvisoExperiencia());
        assertEquals(30, servicio.diasAvisoMensual());
    }

    @Test
    void unaConfiguracionDanadaVuelveALosValoresPorDefecto() {
        configuracion.guardar(RecordatorioService.CLAVE_DIAS_EXPERIENCIA, "mucho");
        configuracion.guardar(RecordatorioService.CLAVE_DIAS_MENSUAL, "-4");

        assertEquals(7, servicio.diasAvisoExperiencia());
        assertEquals(3, servicio.diasAvisoMensual());
    }

    @Test
    void siUnValorInvalidoNoSeGuardaLosAnterioresSeConservan() {
        servicio.definirDiasAviso(9, 4);

        assertThrows(AppException.class, () -> servicio.definirDiasAviso(12, 99));

        assertEquals(9, servicio.diasAvisoExperiencia());
        assertEquals(4, servicio.diasAvisoMensual());
    }

    // ---------- una vez al día ----------

    @Test
    void elAvisoDelDiaSoloSeMuestraUnaVezPorDia() {
        cobro("Pago semanal", -1);

        assertEquals(1, servicio.avisoDelDia().size());
        assertTrue(servicio.avisoDelDia().isEmpty());
        assertEquals(HOY.toString(), configuracion.obtener(RecordatorioService.CLAVE_ULTIMO_AVISO).orElseThrow());
    }

    @Test
    void alDiaSiguienteElAvisoVuelveAMostrarse() {
        cobro("Pago semanal", -1);
        servicio.avisoDelDia();
        LocalDate manana = HOY.plusDays(1);
        Clock relojManana = Clock.fixed(manana.atStartOfDay(ZONA).toInstant(), ZONA);
        RecordatorioService alDiaSiguiente = new RecordatorioService(usuarioDao, pendienteDao, configuracion,
                relojManana);

        List<Recordatorio> lista = alDiaSiguiente.avisoDelDia();

        assertEquals(1, lista.size());
        assertEquals(-2, lista.get(0).dias());
        assertTrue(alDiaSiguiente.avisoDelDia().isEmpty());
    }

    @Test
    void sinNadaQueAvisarNoSeGastaElAvisoDelDia() {
        assertTrue(servicio.avisoDelDia().isEmpty());
        assertTrue(configuracion.obtener(RecordatorioService.CLAVE_ULTIMO_AVISO).isEmpty());

        cobro("Pago semanal", -1);

        assertEquals(1, servicio.avisoDelDia().size());
    }

    @Test
    void elBotonDeCobrosPendientesPuedeConsultarLosRecordatoriosLasVecesQueSea() {
        cobro("Pago semanal", -1);
        servicio.avisoDelDia();

        assertEquals(1, servicio.recordatorios().size());
        assertEquals(1, servicio.recordatorios().size());
    }

    // ---------- integración con la generación automática ----------

    @Test
    void unUsuarioMensualRecienRegistradoAvisaCuandoSeAcercaSuFecha() {
        UsuarioAtendido mensual = new UsuarioAtendido(0, "Beto Prueba", HOY, HOY, null, false, Frecuencia.MENSUAL,
                new BigDecimal("3500"), null, 27, false, null, null, List.of());
        long id = usuarioDao.insertar(mensual);
        cobros.generarPara(mensual.conId(id));

        List<Recordatorio> lista = avisos();

        assertEquals(1, lista.size());
        assertEquals(ColorAviso.VERDE, lista.get(0).color());
        assertEquals(LocalDate.of(2026, 11, 27), lista.get(0).cobro().fechaLimite());
    }
}
