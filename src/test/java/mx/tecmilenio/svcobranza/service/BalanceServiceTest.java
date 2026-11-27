package mx.tecmilenio.svcobranza.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import mx.tecmilenio.svcobranza.model.Balance;
import mx.tecmilenio.svcobranza.model.EstadoPendiente;
import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Movimiento;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.ReporteUsuario;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;
import mx.tecmilenio.svcobranza.model.UsuarioAtendido;

class BalanceServiceTest {

    /** Martes 24 de noviembre de 2026: la semana va del lunes 23 al domingo 29. */
    private static final LocalDate HOY = LocalDate.of(2026, 11, 24);

    private Database db;
    private UsuarioDao usuarios;
    private PendienteDao pendienteDao;
    private MovimientoService movimientos;
    private BalanceService balance;

    @BeforeEach
    void preparar() {
        db = Database.enMemoria("balance" + System.nanoTime());
        ZoneId zona = ZoneId.of("America/Mexico_City");
        Clock reloj = Clock.fixed(HOY.atStartOfDay(zona).toInstant(), zona);
        BitacoraService bitacora = new BitacoraService(new BitacoraDao(db), reloj);
        usuarios = new UsuarioDao(db);
        MovimientoDao movimientoDao = new MovimientoDao(db);
        pendienteDao = new PendienteDao(db);
        movimientos = new MovimientoService(db, movimientoDao, new CategoriaDao(db), usuarios, bitacora);
        balance = new BalanceService(movimientoDao, pendienteDao, reloj);
    }

    @AfterEach
    void cerrar() {
        db.close();
    }

    /** Registra al usuario si todavia no existe y devuelve su id. */
    private long usuario(String nombre) {
        return usuarios.buscarPorNombre(nombre).map(UsuarioAtendido::id)
                .orElseGet(() -> usuarios.insertar(new UsuarioAtendido(0, nombre, HOY, HOY, null, true, null, null,
                        null, null, false, null, null, List.of())));
    }

    private Movimiento ingreso(LocalDate fecha, String monto, String categoria, String usuario) {
        usuario(usuario);
        return movimientos.registrar(new Movimiento(0, TipoMovimiento.INGRESO, fecha, new BigDecimal(monto),
                "Ingreso de prueba", categoria, usuario, FormaPago.EFECTIVO, false), true);
    }

    private Movimiento egreso(LocalDate fecha, String monto, String categoria) {
        return movimientos.registrar(new Movimiento(0, TipoMovimiento.EGRESO, fecha, new BigDecimal(monto),
                "Egreso de prueba", categoria, null, null, false), true);
    }

    private Pendiente cobro(String usuario, String categoria, LocalDate fecha, String monto, EstadoPendiente estado) {
        long usuarioId = usuario(usuario);
        long id = pendienteDao.insertar(new Pendiente(0, usuarioId, usuario, categoria, fecha, new BigDecimal(monto),
                estado, null, null));
        return pendienteDao.buscarPorId(id).orElseThrow();
    }

    // ---------- RF-BAL-01 ----------

    @Test
    void calculaTotalesYSaldoDelPeriodo() {
        ingreso(HOY, "1000", "Tiendita", "Ana Prueba");
        ingreso(HOY.plusDays(1), "500.50", "Experiencia", "Beto Prueba");
        egreso(HOY, "300.25", "Luz");

        Balance b = balance.calcular(HOY.minusDays(1), HOY.plusDays(2));

        assertEquals(new BigDecimal("1500.50"), b.totalIngresos());
        assertEquals(new BigDecimal("300.25"), b.totalEgresos());
        assertEquals(new BigDecimal("1200.25"), b.saldo());
    }

    @Test
    void incluyeLosDiasExtremosDelRango() {
        ingreso(HOY, "100", "Tiendita", "Ana Prueba");
        ingreso(HOY.plusDays(5), "200", "Tiendita", "Ana Prueba");
        ingreso(HOY.plusDays(6), "400", "Tiendita", "Ana Prueba");

        Balance b = balance.calcular(HOY, HOY.plusDays(5));

        assertEquals(new BigDecimal("300.00"), b.totalIngresos());
    }

    @Test
    void ignoraLosMovimientosCancelados() {
        Movimiento m = ingreso(HOY, "700", "Tiendita", "Ana Prueba");
        ingreso(HOY, "100", "Experiencia", "Beto Prueba");
        egreso(HOY, "50", "Luz");
        movimientos.cancelar(m.id());

        Balance b = balance.calcular(HOY, HOY);

        assertEquals(new BigDecimal("100.00"), b.totalIngresos());
    }

    @Test
    void sinMovimientosTodoEsCero() {
        Balance b = balance.calcular(HOY, HOY.plusDays(6));
        assertEquals(0, b.totalIngresos().signum());
        assertEquals(0, b.totalEgresos().signum());
        assertEquals(0, b.saldo().signum());
    }

    @Test
    void sumaCentavosSinErroresDeRedondeo() {
        for (int i = 0; i < 10; i++) {
            ingreso(HOY, "0.10", "Tiendita", "Persona " + i);
        }
        egreso(HOY, "0.30", "Luz");

        Balance b = balance.calcular(HOY, HOY);

        assertEquals(new BigDecimal("1.00"), b.totalIngresos());
        assertEquals(new BigDecimal("0.70"), b.saldo());
    }

    @Test
    void laSemanaActualVaDeLunesADomingo() {
        ingreso(LocalDate.of(2026, 11, 22), "999", "Tiendita", "Ana Prueba");
        ingreso(LocalDate.of(2026, 11, 23), "100", "Tiendita", "Ana Prueba");
        ingreso(LocalDate.of(2026, 11, 29), "200", "Tiendita", "Ana Prueba");
        ingreso(LocalDate.of(2026, 11, 30), "999", "Tiendita", "Ana Prueba");

        Balance semana = balance.semanaActual();

        assertEquals(LocalDate.of(2026, 11, 23), semana.desde());
        assertEquals(LocalDate.of(2026, 11, 29), semana.hasta());
        assertEquals(new BigDecimal("300.00"), semana.totalIngresos());
    }

    @Test
    void rechazaFechasFaltantesOInvertidas() {
        assertThrows(AppException.class, () -> balance.calcular(null, HOY));
        assertThrows(AppException.class, () -> balance.calcular(HOY, null));
        assertThrows(AppException.class, () -> balance.calcular(HOY, HOY.minusDays(1)));
    }

    // ---------- RF-BAL-02 ----------

    @Test
    void avisaCuandoLosEgresosSuperanALosIngresos() {
        ingreso(HOY, "100", "Tiendita", "Ana Prueba");
        egreso(HOY, "100.01", "Renta");
        assertTrue(balance.calcular(HOY, HOY).egresosSuperanIngresos());
    }

    @Test
    void noAvisaSiSonIgualesOMenores() {
        ingreso(HOY, "100", "Tiendita", "Ana Prueba");
        egreso(HOY, "100", "Renta");
        assertFalse(balance.calcular(HOY, HOY).egresosSuperanIngresos());

        egreso(HOY.plusDays(1), "1", "Luz");
        assertFalse(balance.calcular(HOY, HOY).egresosSuperanIngresos());
    }

    // ---------- RF-BAL-03 ----------

    @Test
    void calculaElPorcentajeDeCadaCategoria() {
        ingreso(HOY, "750", "Tiendita", "Ana Prueba");
        ingreso(HOY, "250", "Experiencia", "Beto Prueba");

        Balance b = balance.calcular(HOY, HOY);

        assertEquals(new BigDecimal("75.00"), b.porcentajesIngresos().get("Tiendita"));
        assertEquals(new BigDecimal("25.00"), b.porcentajesIngresos().get("Experiencia"));
    }

    @Test
    void losPorcentajesSumanCienConTolerancia() {
        ingreso(HOY, "100", "Tiendita", "A Prueba");
        ingreso(HOY, "100", "Experiencia", "B Prueba");
        ingreso(HOY, "100", "Otros", "C Prueba");

        BigDecimal suma = balance.calcular(HOY, HOY).porcentajesIngresos().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertTrue(suma.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.05")) <= 0);
    }

    @Test
    void sinTotalNoHayPorcentajes() {
        assertTrue(balance.calcular(HOY, HOY).porcentajesIngresos().isEmpty());
        assertTrue(balance.calcular(HOY, HOY).porcentajesEgresos().isEmpty());
    }

    @Test
    void tambienCalculaLosPorcentajesDeEgresos() {
        egreso(HOY, "300", "Renta");
        egreso(HOY, "100", "Luz");
        Balance b = balance.calcular(HOY, HOY);
        assertEquals(new BigDecimal("75.00"), b.porcentajesEgresos().get("Renta"));
        assertEquals(new BigDecimal("25.00"), b.porcentajesEgresos().get("Luz"));
    }

    // ---------- RF-BAL-04 ----------

    @Test
    void elReporteDeUsuarioReunePagosYLoQueAdeuda() {
        ingreso(HOY.minusDays(7), "400", "Pago mensual", "Ana Prueba");
        ingreso(HOY, "250.50", "Tiendita", "Ana Prueba");
        ingreso(HOY, "999", "Tiendita", "Beto Prueba");
        cobro("Ana Prueba", "Pago mensual", HOY.plusDays(3), "600", EstadoPendiente.PENDIENTE);

        ReporteUsuario r = balance.reportePorUsuario("  ana prueba ");

        assertEquals(2, r.movimientos().size());
        assertEquals(HOY.minusDays(7), r.movimientos().get(0).fecha(), "del mas antiguo al mas reciente");
        assertEquals(new BigDecimal("650.50"), r.totalPagado());
        assertEquals(new BigDecimal("600.00"), r.totalAdeudado());
    }

    @Test
    void elReporteNoCuentaLosPagosCanceladosNiLosCobrosPagadosOCondonados() {
        Movimiento m = ingreso(HOY, "400", "Tiendita", "Ana Prueba");
        movimientos.cancelar(m.id());
        ingreso(HOY, "100", "Pago mensual", "Ana Prueba");
        cobro("Ana Prueba", "Pago mensual", HOY, "100", EstadoPendiente.PAGADO);
        cobro("Ana Prueba", "Médico", HOY, "80", EstadoPendiente.CONDONADO);

        ReporteUsuario r = balance.reportePorUsuario("Ana Prueba");

        assertEquals(new BigDecimal("100.00"), r.totalPagado());
        assertEquals(0, r.totalAdeudado().signum());
        assertEquals(2, r.pendientes().size(), "el reporte lista tambien los pagados y condonados");
    }

    @Test
    void elReporteDeUnUsuarioSinDatosEstaVacio() {
        ReporteUsuario r = balance.reportePorUsuario("Nadie Prueba");
        assertTrue(r.movimientos().isEmpty());
        assertEquals(0, r.totalPagado().signum());
    }

    @Test
    void elReporteExigeElNombreDelUsuario() {
        assertThrows(AppException.class, () -> balance.reportePorUsuario("  "));
        assertThrows(AppException.class, () -> balance.reportePorUsuario(null));
    }
}
