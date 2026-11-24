package mx.tecmilenio.svcobranza.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mx.tecmilenio.svcobranza.db.BitacoraDao;
import mx.tecmilenio.svcobranza.db.Database;
import mx.tecmilenio.svcobranza.model.EntradaBitacora;

class BitacoraServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

    private Database db;
    private BitacoraService servicio;

    private static Clock relojEn(String instanteLocal) {
        return Clock.fixed(LocalDateTime.parse(instanteLocal).atZone(ZONA).toInstant(), ZONA);
    }

    @BeforeEach
    void preparar() {
        db = Database.enMemoria("bitacora" + System.nanoTime());
        servicio = new BitacoraService(new BitacoraDao(db), relojEn("2026-11-24T10:30:15"));
    }

    @AfterEach
    void cerrar() {
        db.close();
    }

    @Test
    void registraFechaHoraTipoAccionYMonto() {
        servicio.registrar("INGRESO", "ALTA", new BigDecimal("1500.50"), "Pago semanal de prueba");

        List<EntradaBitacora> entradas = servicio.consultar();
        assertEquals(1, entradas.size());
        EntradaBitacora e = entradas.get(0);
        assertEquals(LocalDateTime.parse("2026-11-24T10:30:15"), e.fechaHora());
        assertEquals("INGRESO", e.tipo());
        assertEquals("ALTA", e.accion());
        assertEquals(new BigDecimal("1500.50"), e.monto());
        assertEquals("Pago semanal de prueba", e.detalle());
    }

    @Test
    void aceptaEntradasSinMonto() {
        servicio.registrar("RESPALDO", "GENERACION", null, "Respaldo semanal");
        assertNull(servicio.consultar().get(0).monto());
    }

    @Test
    void consultaDevuelveLasMasRecientesPrimero() {
        BitacoraDao dao = new BitacoraDao(db);
        dao.registrar(LocalDateTime.parse("2026-11-20T09:00:00"), "INGRESO", "ALTA", BigDecimal.TEN, "antigua");
        dao.registrar(LocalDateTime.parse("2026-11-22T09:00:00"), "EGRESO", "ALTA", BigDecimal.ONE, "reciente");

        List<EntradaBitacora> entradas = servicio.consultar();
        assertEquals("reciente", entradas.get(0).detalle());
        assertEquals("antigua", entradas.get(1).detalle());
    }

    @Test
    void conVariasEntradasDelMismoInstanteConservaElOrdenInverso() {
        servicio.registrar("INGRESO", "ALTA", BigDecimal.ONE, "primera");
        servicio.registrar("INGRESO", "EDICION", BigDecimal.ONE, "segunda");

        List<EntradaBitacora> entradas = servicio.consultar();
        assertEquals("segunda", entradas.get(0).detalle());
        assertEquals("primera", entradas.get(1).detalle());
    }

    @Test
    void siLaTransaccionFallaNoQuedaEntradaHuerfana() {
        try {
            db.transaccion(() -> {
                servicio.registrar("INGRESO", "ALTA", BigDecimal.TEN, "se debe deshacer");
                throw new IllegalStateException("fallo simulado");
            });
        } catch (IllegalStateException esperado) {
            assertEquals("fallo simulado", esperado.getMessage());
        }
        assertTrue(servicio.consultar().isEmpty());
    }

    @Test
    void usaElRelojInyectadoYNoLaHoraReal() {
        BitacoraService otro = new BitacoraService(new BitacoraDao(db),
                Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneId.of("UTC")));
        otro.registrar("EGRESO", "CANCELACION", BigDecimal.ONE, "reloj fijo");
        assertEquals(LocalDateTime.parse("2030-01-01T12:00:00"), otro.consultar().get(0).fechaHora());
    }
}
