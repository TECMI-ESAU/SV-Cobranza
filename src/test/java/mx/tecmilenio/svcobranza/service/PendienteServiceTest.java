package mx.tecmilenio.svcobranza.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

class PendienteServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 24);

    private Database db;
    private MovimientoService movimientos;
    private PendienteService servicio;
    private BitacoraService bitacora;

    @BeforeEach
    void preparar() {
        db = Database.enMemoria("pendientes" + System.nanoTime());
        ZoneId zona = ZoneId.of("America/Mexico_City");
        Clock reloj = Clock.fixed(HOY.atStartOfDay(zona).toInstant(), zona);
        bitacora = new BitacoraService(new BitacoraDao(db), reloj);
        movimientos = new MovimientoService(db, new MovimientoDao(db, new UsuarioDao(db)), new CategoriaDao(db),
                bitacora);
        servicio = new PendienteService(db, new PendienteDao(db), movimientos, bitacora, reloj);
    }

    @AfterEach
    void cerrar() {
        db.close();
    }

    private static Pendiente porCobrar(String persona, String monto, LocalDate limite, String categoria) {
        return new Pendiente(0, TipoPendiente.POR_COBRAR, limite, new BigDecimal(monto), "Pago de " + categoria,
                categoria, persona, false, null);
    }

    private static Pendiente porPagar(String proveedor, String monto, LocalDate limite) {
        return new Pendiente(0, TipoPendiente.POR_PAGAR, limite, new BigDecimal(monto), "Recibo", "Luz", proveedor,
                false, null);
    }

    // ---------- RF-PEN-01 ----------

    @Test
    void registraUnPendientePorCobrarYUnoPorPagar() {
        Pendiente a = servicio.registrar(porCobrar("Ana Prueba", "800", HOY.plusDays(5), "Tiendita"));
        Pendiente b = servicio.registrar(porPagar("Proveedor de prueba", "300", HOY.plusDays(2)));

        assertTrue(a.id() > 0);
        assertTrue(b.id() > 0);
        assertFalse(a.pagado());
        assertEquals(2, servicio.todos().size());
        assertEquals(TipoPendiente.POR_PAGAR, servicio.todos().get(1).tipo());
    }

    @Test
    void elAltaDelPendienteQuedaEnLaBitacora() {
        servicio.registrar(porCobrar("Ana Prueba", "800", HOY.plusDays(5), "Tiendita"));
        assertEquals("PENDIENTE", bitacora.consultar().get(0).tipo());
        assertEquals("ALTA", bitacora.consultar().get(0).accion());
    }

    @Test
    void rechazaDatosIncompletosOInvalidos() {
        assertThrows(AppException.class,
                () -> servicio.registrar(porCobrar("Ana Prueba", "0", HOY, "Tiendita")));
        assertThrows(AppException.class,
                () -> servicio.registrar(porCobrar("   ", "100", HOY, "Tiendita")));
        assertThrows(AppException.class,
                () -> servicio.registrar(porCobrar("Ana Prueba", "100", null, "Tiendita")));
        assertThrows(AppException.class,
                () -> servicio.registrar(porCobrar("Ana Prueba", "100", HOY, "Renta")));
        assertTrue(servicio.todos().isEmpty());
    }

    // ---------- RF-PEN-02 ----------

    @Test
    void alPagarUnPendientePorCobrarSeCreaElIngreso() {
        Pendiente p = servicio.registrar(porCobrar("Ana Prueba", "800", HOY.plusDays(5), "Tiendita"));

        Movimiento ingreso = servicio.marcarPagado(p.id(), HOY, FormaPago.TRANSFERENCIA);

        assertEquals(TipoMovimiento.INGRESO, ingreso.tipo());
        assertEquals(new BigDecimal("800.00"), ingreso.monto());
        assertEquals("Ana Prueba", ingreso.usuario());
        assertEquals("Tiendita", ingreso.categoria());
        assertEquals(FormaPago.TRANSFERENCIA, ingreso.formaPago());
        Pendiente actualizado = servicio.todos().get(0);
        assertTrue(actualizado.pagado());
        assertEquals(ingreso.id(), actualizado.movimientoId());
    }

    @Test
    void alPagarUnPendientePorPagarSeCreaElEgreso() {
        Pendiente p = servicio.registrar(porPagar("Proveedor de prueba", "300", HOY.plusDays(2)));

        Movimiento egreso = servicio.marcarPagado(p.id(), HOY, null);

        assertEquals(TipoMovimiento.EGRESO, egreso.tipo());
        assertEquals("Proveedor de prueba", egreso.proveedor());
        assertEquals("Luz", egreso.categoria());
        assertEquals(1, movimientos.buscar(FiltroMovimiento.de(TipoMovimiento.EGRESO)).size());
    }

    @Test
    void noSePuedePagarDosVecesElMismoPendiente() {
        Pendiente p = servicio.registrar(porCobrar("Ana Prueba", "800", HOY, "Tiendita"));
        servicio.marcarPagado(p.id(), HOY, FormaPago.EFECTIVO);

        assertThrows(AppException.class, () -> servicio.marcarPagado(p.id(), HOY, FormaPago.EFECTIVO));
        assertEquals(1, movimientos.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).size());
    }

    @Test
    void cobrarSinFormaDePagoNoCambiaNada() {
        Pendiente p = servicio.registrar(porCobrar("Ana Prueba", "800", HOY, "Tiendita"));

        assertThrows(AppException.class, () -> servicio.marcarPagado(p.id(), HOY, null));

        assertFalse(servicio.todos().get(0).pagado());
        assertTrue(movimientos.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).isEmpty());
    }

    @Test
    void pagarUnPendienteQueYaTieneUnIngresoIgualNoPideConfirmacionDeDuplicado() {
        movimientos.registrar(new Movimiento(0, TipoMovimiento.INGRESO, HOY, new BigDecimal("800"), "otro",
                "Tiendita", "Beto Prueba", FormaPago.EFECTIVO, null, false), false);
        Pendiente p = servicio.registrar(porCobrar("Ana Prueba", "800", HOY, "Tiendita"));

        Movimiento pagado = servicio.marcarPagado(p.id(), HOY, FormaPago.EFECTIVO);

        assertNotNull(pagado);
        assertEquals(2, movimientos.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).size());
    }

    @Test
    void siFallaElPagoNoQuedaNadaAMedias() {
        Pendiente p = servicio.registrar(porCobrar("Ana Prueba", "800", HOY, "Tiendita"));
        // Fecha de pago nula: la validacion falla antes de tocar nada
        assertThrows(AppException.class, () -> servicio.marcarPagado(p.id(), null, FormaPago.EFECTIVO));
        assertFalse(servicio.todos().get(0).pagado());
        assertTrue(movimientos.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).isEmpty());
    }

    @Test
    void elPagoQuedaEnLaBitacora() {
        Pendiente p = servicio.registrar(porCobrar("Ana Prueba", "800", HOY, "Tiendita"));
        servicio.marcarPagado(p.id(), HOY, FormaPago.EFECTIVO);
        long pagos = bitacora.consultar().stream().filter(e -> e.accion().equals("PAGO")).count();
        assertEquals(1, pagos);
    }

    @Test
    void pagarUnPendienteInexistenteDaUnErrorClaro() {
        AppException e = assertThrows(AppException.class, () -> servicio.marcarPagado(999, HOY, FormaPago.EFECTIVO));
        assertTrue(e.getMessage().contains("no existe"));
    }

    // ---------- RF-PEN-03 ----------

    @Test
    void lasAlertasIncluyenVencidosYLosQueVencenEnSieteDias() {
        servicio.registrar(porCobrar("Vencido Prueba", "100", HOY.minusDays(3), "Tiendita"));
        servicio.registrar(porCobrar("Hoy Prueba", "100", HOY, "Tiendita"));
        servicio.registrar(porPagar("Proveedor", "100", HOY.plusDays(7)));
        servicio.registrar(porCobrar("Lejano Prueba", "100", HOY.plusDays(8), "Tiendita"));

        List<Pendiente> alertas = servicio.alertas();

        assertEquals(3, alertas.size());
        assertEquals("Vencido Prueba", alertas.get(0).contraparte());
        assertTrue(alertas.stream().noneMatch(p -> p.contraparte().equals("Lejano Prueba")));
    }

    @Test
    void lasAlertasNoIncluyenLosPendientesYaPagados() {
        Pendiente p = servicio.registrar(porCobrar("Ana Prueba", "100", HOY.minusDays(1), "Tiendita"));
        servicio.marcarPagado(p.id(), HOY, FormaPago.EFECTIVO);
        assertTrue(servicio.alertas().isEmpty());
    }

    @Test
    void distingueLosVencidosDeLosProximos() {
        Pendiente vencido = servicio.registrar(porCobrar("A Prueba", "100", HOY.minusDays(1), "Tiendita"));
        Pendiente deHoy = servicio.registrar(porCobrar("B Prueba", "100", HOY, "Tiendita"));
        assertTrue(servicio.estaVencido(vencido));
        assertFalse(servicio.estaVencido(deHoy));
    }

    @Test
    void sinPendientesNoHayAlertas() {
        assertTrue(servicio.alertas().isEmpty());
    }

    // ---------- RF-PEN-04 ----------

    @Test
    void consultaQuienYaPagoYQuienNoPorCategoria() {
        Pendiente ana = servicio.registrar(porCobrar("Ana Prueba", "100", HOY, "Tiendita"));
        servicio.registrar(porCobrar("Beto Prueba", "100", HOY, "Tiendita"));
        servicio.registrar(porCobrar("Carla Prueba", "100", HOY, "Experiencia"));
        servicio.marcarPagado(ana.id(), HOY, FormaPago.EFECTIVO);

        List<Pendiente> tiendita = servicio.porCategoria("Tiendita");

        assertEquals(2, tiendita.size());
        assertEquals("Beto Prueba", tiendita.get(0).contraparte(), "los pendientes sin pagar van primero");
        assertFalse(tiendita.get(0).pagado());
        assertEquals("Ana Prueba", tiendita.get(1).contraparte());
        assertTrue(tiendita.get(1).pagado());
        assertEquals(1, servicio.porCategoria("Experiencia").size());
    }
}
