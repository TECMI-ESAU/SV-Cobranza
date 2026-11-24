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
import mx.tecmilenio.svcobranza.db.UsuarioDao;
import mx.tecmilenio.svcobranza.model.EntradaBitacora;
import mx.tecmilenio.svcobranza.model.FiltroMovimiento;
import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Movimiento;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;

class MovimientoServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 24);

    private Database db;
    private MovimientoService servicio;
    private BitacoraService bitacora;

    @BeforeEach
    void preparar() {
        db = Database.enMemoria("movimientos" + System.nanoTime());
        Clock reloj = Clock.fixed(HOY.atStartOfDay(ZoneId.of("America/Mexico_City")).toInstant(),
                ZoneId.of("America/Mexico_City"));
        bitacora = new BitacoraService(new BitacoraDao(db), reloj);
        servicio = new MovimientoService(db, new MovimientoDao(db, new UsuarioDao(db)), new CategoriaDao(db),
                bitacora);
    }

    @AfterEach
    void cerrar() {
        db.close();
    }

    private static Movimiento ingreso(String monto, String usuario, String concepto) {
        return new Movimiento(0, TipoMovimiento.INGRESO, HOY, new BigDecimal(monto), concepto, "Tiendita",
                usuario, FormaPago.EFECTIVO, null, false);
    }

    private static Movimiento egreso(String monto, String proveedor, String concepto) {
        return new Movimiento(0, TipoMovimiento.EGRESO, HOY, new BigDecimal(monto), concepto, "Abasto", null,
                null, proveedor, false);
    }

    // ---------- RF-ING-01 / RF-EGR-01: registrar ----------

    @Test
    void registraUnIngresoConTodosSusDatos() {
        Movimiento guardado = servicio.registrar(ingreso("1500.5", "Persona Uno", "Tiendita semana 1"), false);

        assertTrue(guardado.id() > 0);
        Movimiento leido = servicio.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).get(0);
        assertEquals(new BigDecimal("1500.50"), leido.monto());
        assertEquals("Persona Uno", leido.usuario());
        assertEquals(FormaPago.EFECTIVO, leido.formaPago());
        assertEquals("Tiendita", leido.categoria());
        assertEquals(HOY, leido.fecha());
    }

    @Test
    void registraUnEgresoConProveedor() {
        servicio.registrar(egreso("820", "Proveedor de prueba", "Despensa"), false);

        Movimiento leido = servicio.buscar(FiltroMovimiento.de(TipoMovimiento.EGRESO)).get(0);
        assertEquals("Proveedor de prueba", leido.proveedor());
        assertEquals("Abasto", leido.categoria());
        assertEquals(new BigDecimal("820.00"), leido.monto());
    }

    @Test
    void limpiaEspaciosSobrantesDelTexto() {
        servicio.registrar(ingreso("100", "  Persona   Dos  ", "  pago   de   prueba "), false);
        Movimiento leido = servicio.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).get(0);
        assertEquals("Persona Dos", leido.usuario());
        assertEquals("pago de prueba", leido.concepto());
    }

    @Test
    void rechazaMontoCeroONegativo() {
        assertThrows(AppException.class, () -> servicio.registrar(ingreso("0", "Persona", "x"), false));
        assertThrows(AppException.class, () -> servicio.registrar(ingreso("-5", "Persona", "x"), false));
    }

    @Test
    void rechazaCamposObligatoriosVacios() {
        assertThrows(AppException.class, () -> servicio.registrar(ingreso("10", "Persona", "   "), false));
        assertThrows(AppException.class, () -> servicio.registrar(ingreso("10", "  ", "concepto"), false));
        Movimiento sinForma = new Movimiento(0, TipoMovimiento.INGRESO, HOY, BigDecimal.TEN, "x", "Tiendita",
                "Persona", null, null, false);
        assertThrows(AppException.class, () -> servicio.registrar(sinForma, false));
    }

    @Test
    void elMensajeDeErrorListaTodosLosProblemas() {
        Movimiento malo = new Movimiento(0, TipoMovimiento.INGRESO, null, BigDecimal.ZERO, "", "Nada", "", null,
                null, false);
        AppException e = assertThrows(AppException.class, () -> servicio.registrar(malo, false));
        assertTrue(e.getMessage().contains("fecha"));
        assertTrue(e.getMessage().contains("monto"));
        assertTrue(e.getMessage().contains("concepto"));
        assertTrue(e.getMessage().contains("categoría"));
    }

    // ---------- RF-ING-02 / RF-EGR-02: categorias ----------

    @Test
    void ofreceSoloLasCategoriasDeCadaTipo() {
        assertEquals(6, servicio.categorias(TipoMovimiento.INGRESO).size());
        assertEquals(6, servicio.categorias(TipoMovimiento.EGRESO).size());
        assertFalse(servicio.categorias(TipoMovimiento.INGRESO).contains("Renta"));
        assertFalse(servicio.categorias(TipoMovimiento.EGRESO).contains("Tiendita"));
    }

    @Test
    void rechazaUnaCategoriaDeOtroTipo() {
        Movimiento ingresoConCategoriaDeEgreso = new Movimiento(0, TipoMovimiento.INGRESO, HOY, BigDecimal.TEN,
                "x", "Renta", "Persona", FormaPago.EFECTIVO, null, false);
        assertThrows(AppException.class, () -> servicio.registrar(ingresoConCategoriaDeEgreso, false));
    }

    // ---------- RF-TRA-01: duplicados ----------

    @Test
    void avisaCuandoYaExisteUnMovimientoConMismoTipoFechaYMonto() {
        servicio.registrar(ingreso("500", "Persona Uno", "primer pago"), false);

        PosibleDuplicadoException e = assertThrows(PosibleDuplicadoException.class,
                () -> servicio.registrar(ingreso("500", "Persona Dos", "otro pago"), false));
        assertEquals(1, e.parecidos().size());
        assertEquals("primer pago", e.parecidos().get(0).concepto());
        assertEquals(1, servicio.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).size());
    }

    @Test
    void elUsuarioPuedeConfirmarYGuardarDeTodosModos() {
        servicio.registrar(ingreso("500", "Persona Uno", "primer pago"), false);
        servicio.registrar(ingreso("500", "Persona Dos", "otro pago"), true);
        assertEquals(2, servicio.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).size());
    }

    @Test
    void unEgresoConElMismoMontoNoEsDuplicadoDeUnIngreso() {
        servicio.registrar(ingreso("500", "Persona Uno", "pago"), false);
        servicio.registrar(egreso("500", "Proveedor", "compra"), false);
        assertEquals(1, servicio.buscar(FiltroMovimiento.de(TipoMovimiento.EGRESO)).size());
    }

    @Test
    void otraFechaOOtroMontoNoSonDuplicado() {
        servicio.registrar(ingreso("500", "Persona Uno", "pago"), false);
        servicio.registrar(ingreso("500.01", "Persona Uno", "pago"), false);
        Movimiento otroDia = new Movimiento(0, TipoMovimiento.INGRESO, HOY.minusDays(1), new BigDecimal("500"),
                "pago", "Tiendita", "Persona Uno", FormaPago.EFECTIVO, null, false);
        servicio.registrar(otroDia, false);
        assertEquals(3, servicio.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).size());
    }

    @Test
    void unMovimientoCanceladoNoCuentaComoDuplicado() {
        Movimiento primero = servicio.registrar(ingreso("500", "Persona Uno", "pago"), false);
        servicio.cancelar(primero.id());
        servicio.registrar(ingreso("500", "Persona Uno", "pago corregido"), false);
        assertEquals(1, servicio.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).size());
    }

    // ---------- RF-ING-03 / RF-EGR-03: buscar ----------

    private void cargarDatosDeBusqueda() {
        servicio.registrar(ingreso("100", "Ana Prueba", "Tiendita semana 1"), false);
        servicio.registrar(new Movimiento(0, TipoMovimiento.INGRESO, HOY.minusDays(10), new BigDecimal("250"),
                "Experiencia mensual", "Experiencia", "Beto Prueba", FormaPago.DEPOSITO, null, false), false);
        servicio.registrar(new Movimiento(0, TipoMovimiento.INGRESO, HOY.minusDays(3), new BigDecimal("900"),
                "Pago mensual", "Pago semanal o mensual", "Ana Prueba", FormaPago.TRANSFERENCIA, null, false),
                false);
        servicio.registrar(egreso("300", "Luz y Fuerza de prueba", "Recibo de luz"), false);
    }

    private static FiltroMovimiento filtro(TipoMovimiento tipo, String persona, String concepto, LocalDate desde,
            LocalDate hasta, String min, String max) {
        return new FiltroMovimiento(tipo, persona, concepto, desde, hasta,
                min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max), false);
    }

    @Test
    void filtraPorNombreDeUsuarioSinImportarMayusculas() {
        cargarDatosDeBusqueda();
        List<Movimiento> r = servicio.buscar(filtro(TipoMovimiento.INGRESO, "ana", null, null, null, null, null));
        assertEquals(2, r.size());
        assertTrue(r.stream().allMatch(m -> m.usuario().equals("Ana Prueba")));
    }

    @Test
    void filtraPorConcepto() {
        cargarDatosDeBusqueda();
        List<Movimiento> r = servicio.buscar(filtro(TipoMovimiento.INGRESO, null, "mensual", null, null, null, null));
        assertEquals(2, r.size());
    }

    @Test
    void filtraPorRangoDeFechasIncluyendoLosExtremos() {
        cargarDatosDeBusqueda();
        List<Movimiento> r = servicio.buscar(
                filtro(TipoMovimiento.INGRESO, null, null, HOY.minusDays(3), HOY, null, null));
        assertEquals(2, r.size());
    }

    @Test
    void filtraPorRangoDeMontos() {
        cargarDatosDeBusqueda();
        List<Movimiento> r = servicio.buscar(filtro(TipoMovimiento.INGRESO, null, null, null, null, "200", "900"));
        assertEquals(2, r.size());
    }

    @Test
    void combinaVariosFiltros() {
        cargarDatosDeBusqueda();
        List<Movimiento> r = servicio.buscar(
                filtro(TipoMovimiento.INGRESO, "ana", "mensual", null, null, "500", null));
        assertEquals(1, r.size());
        assertEquals(new BigDecimal("900.00"), r.get(0).monto());
    }

    @Test
    void losEgresosSeFiltranPorProveedorYConcepto() {
        cargarDatosDeBusqueda();
        assertEquals(1, servicio.buscar(filtro(TipoMovimiento.EGRESO, "luz", "recibo", null, null, null, null)).size());
        assertEquals(0, servicio.buscar(filtro(TipoMovimiento.EGRESO, "otro", null, null, null, null, null)).size());
    }

    @Test
    void devuelveLosMasRecientesPrimero() {
        cargarDatosDeBusqueda();
        List<Movimiento> r = servicio.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO));
        assertEquals(HOY, r.get(0).fecha());
        assertEquals(HOY.minusDays(10), r.get(2).fecha());
    }

    // ---------- RF-ING-04 / RF-EGR-04: editar y cancelar ----------

    @Test
    void editaUnMovimientoYDejaElAntesYElDespuesEnLaBitacora() {
        Movimiento original = servicio.registrar(ingreso("100", "Persona Uno", "pago"), false);
        Movimiento cambios = new Movimiento(original.id(), TipoMovimiento.INGRESO, HOY, new BigDecimal("120"),
                "pago corregido", "Tiendita", "Persona Uno", FormaPago.EFECTIVO, null, false);

        servicio.editar(cambios);

        Movimiento leido = servicio.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).get(0);
        assertEquals(new BigDecimal("120.00"), leido.monto());
        assertEquals("pago corregido", leido.concepto());
        EntradaBitacora ultima = bitacora.consultar().get(0);
        assertEquals("EDICION", ultima.accion());
        assertTrue(ultima.detalle().contains("Antes"));
        assertTrue(ultima.detalle().contains("Ahora"));
    }

    @Test
    void laEdicionTambienValidaLosDatos() {
        Movimiento original = servicio.registrar(ingreso("100", "Persona Uno", "pago"), false);
        Movimiento malo = new Movimiento(original.id(), TipoMovimiento.INGRESO, HOY, new BigDecimal("-1"), "pago",
                "Tiendita", "Persona Uno", FormaPago.EFECTIVO, null, false);
        assertThrows(AppException.class, () -> servicio.editar(malo));
    }

    @Test
    void cancelaUnMovimientoSinBorrarloYLoOcultaPorDefecto() {
        Movimiento m = servicio.registrar(ingreso("100", "Persona Uno", "pago"), false);

        servicio.cancelar(m.id());

        assertTrue(servicio.buscar(FiltroMovimiento.de(TipoMovimiento.INGRESO)).isEmpty());
        FiltroMovimiento conCancelados = new FiltroMovimiento(TipoMovimiento.INGRESO, null, null, null, null, null,
                null, true);
        List<Movimiento> todos = servicio.buscar(conCancelados);
        assertEquals(1, todos.size());
        assertTrue(todos.get(0).cancelado());
        assertEquals("CANCELACION", bitacora.consultar().get(0).accion());
    }

    @Test
    void noSePuedeCancelarDosVecesNiEditarUnCancelado() {
        Movimiento m = servicio.registrar(ingreso("100", "Persona Uno", "pago"), false);
        servicio.cancelar(m.id());
        assertThrows(AppException.class, () -> servicio.cancelar(m.id()));
        assertThrows(AppException.class, () -> servicio.editar(m));
    }

    @Test
    void cancelarUnMovimientoInexistenteDaUnErrorClaro() {
        AppException e = assertThrows(AppException.class, () -> servicio.cancelar(9999));
        assertTrue(e.getMessage().contains("no existe"));
    }

    // ---------- RF-BIT-01: cada cambio deja constancia ----------

    @Test
    void cadaAltaEdicionYCancelacionQuedaEnLaBitacora() {
        Movimiento m = servicio.registrar(ingreso("100", "Persona Uno", "pago"), false);
        servicio.editar(new Movimiento(m.id(), m.tipo(), m.fecha(), new BigDecimal("110"), m.concepto(),
                m.categoria(), m.usuario(), m.formaPago(), null, false));
        servicio.cancelar(m.id());

        List<EntradaBitacora> entradas = bitacora.consultar();
        assertEquals(3, entradas.size());
        assertEquals("CANCELACION", entradas.get(0).accion());
        assertEquals("EDICION", entradas.get(1).accion());
        assertEquals("ALTA", entradas.get(2).accion());
        assertEquals("INGRESO", entradas.get(2).tipo());
    }

    @Test
    void unRegistroInvalidoNoDejaEntradaEnLaBitacora() {
        assertThrows(AppException.class, () -> servicio.registrar(ingreso("0", "Persona", "x"), false));
        assertTrue(bitacora.consultar().isEmpty());
    }
}
