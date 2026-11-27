package mx.tecmilenio.svcobranza.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.db.BitacoraDao;
import mx.tecmilenio.svcobranza.db.Database;
import mx.tecmilenio.svcobranza.db.ExperienciaDao;

class ExperienciaServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 24);
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

    private Database db;
    private ExperienciaDao dao;
    private BitacoraService bitacora;
    private ExperienciaService servicio;

    @BeforeEach
    void preparar() {
        db = Database.enMemoria("experiencia" + System.nanoTime());
        dao = new ExperienciaDao(db);
        bitacora = new BitacoraService(new BitacoraDao(db), reloj(HOY));
        servicio = new ExperienciaService(db, dao, bitacora, reloj(HOY));
    }

    @AfterEach
    void cerrar() {
        db.close();
    }

    private static Clock reloj(LocalDate dia) {
        return Clock.fixed(dia.atStartOfDay(ZONA).toInstant(), ZONA);
    }

    private static void assertCadaVeintiochoDias(List<LocalDate> fechas) {
        for (int i = 1; i < fechas.size(); i++) {
            assertEquals(fechas.get(i - 1).plusDays(28), fechas.get(i), "posición " + i);
        }
    }

    @Test
    void sinFechaInicialNoHayCalendarioYCompletarNoHaceNada() {
        assertTrue(servicio.sinCalendario());
        servicio.completar();
        assertTrue(servicio.fechas().isEmpty());
    }

    @Test
    void laPrimeraFechaGeneraDoceFechasCadaVeintiochoDias() {
        LocalDate primera = HOY.plusDays(3);

        servicio.definirPrimeraFecha(primera);

        List<LocalDate> fechas = servicio.fechas();
        assertFalse(servicio.sinCalendario());
        assertEquals(12, fechas.size());
        assertEquals(primera, fechas.get(0));
        assertEquals(primera.plusDays(28L * 11), fechas.get(11));
        assertCadaVeintiochoDias(fechas);
        assertEquals("EXPERIENCIA", bitacora.consultar().get(0).tipo());
    }

    @Test
    void siLaPrimeraFechaYaPasoSeCompletanDoceDeHoyEnAdelante() {
        LocalDate primera = HOY.minusDays(60);

        servicio.definirPrimeraFecha(primera);

        List<LocalDate> fechas = servicio.fechas();
        assertEquals(primera, fechas.get(0));
        assertCadaVeintiochoDias(fechas);
        assertEquals(12, fechas.stream().filter(f -> !f.isBefore(HOY)).count());
        assertEquals(15, fechas.size());
    }

    @Test
    void noPermiteDefinirLaPrimeraFechaDosVecesNiVacia() {
        assertThrows(AppException.class, () -> servicio.definirPrimeraFecha(null));
        servicio.definirPrimeraFecha(HOY);
        assertThrows(AppException.class, () -> servicio.definirPrimeraFecha(HOY.plusDays(1)));
        assertEquals(12, servicio.fechas().size());
    }

    @Test
    void editarUnaFechaRecorreLasSiguientesYNoTocaLasAnteriores() {
        LocalDate primera = HOY.plusDays(3);
        servicio.definirPrimeraFecha(primera);
        List<LocalDate> antes = servicio.fechas();
        LocalDate tercera = antes.get(2);
        LocalDate nueva = tercera.plusDays(10);

        servicio.editarFecha(tercera, nueva);

        List<LocalDate> despues = servicio.fechas();
        assertEquals(antes.size(), despues.size());
        assertEquals(antes.subList(0, 2), despues.subList(0, 2));
        assertEquals(nueva, despues.get(2));
        assertEquals(nueva.plusDays(28), despues.get(3));
        assertEquals(nueva.plusDays(28L * 9), despues.get(11));
        assertCadaVeintiochoDias(despues.subList(2, despues.size()));
        assertEquals("EDICION", bitacora.consultar().get(0).accion());
    }

    @Test
    void editarLaPrimeraFechaMueveTodoElCalendario() {
        servicio.definirPrimeraFecha(HOY.plusDays(3));

        servicio.editarFecha(HOY.plusDays(3), HOY.plusDays(1));

        List<LocalDate> fechas = servicio.fechas();
        assertEquals(12, fechas.size());
        assertEquals(HOY.plusDays(1), fechas.get(0));
        assertCadaVeintiochoDias(fechas);
    }

    @Test
    void laFechaNuevaDebeSerPosteriorALaAnteriorDelCalendario() {
        servicio.definirPrimeraFecha(HOY.plusDays(3));
        List<LocalDate> antes = servicio.fechas();

        AppException e = assertThrows(AppException.class,
                () -> servicio.editarFecha(antes.get(2), antes.get(1)));

        assertTrue(e.getMessage().contains("posterior"));
        assertEquals(antes, servicio.fechas());
    }

    @Test
    void editarUnaFechaQueNoExisteODejarlaIgualNoCambiaNada() {
        servicio.definirPrimeraFecha(HOY.plusDays(3));
        List<LocalDate> antes = servicio.fechas();

        assertThrows(AppException.class, () -> servicio.editarFecha(HOY.plusDays(4), HOY.plusDays(5)));
        assertThrows(AppException.class, () -> servicio.editarFecha(null, HOY));
        assertThrows(AppException.class, () -> servicio.editarFecha(antes.get(0), null));
        servicio.editarFecha(antes.get(1), antes.get(1));

        assertEquals(antes, servicio.fechas());
    }

    @Test
    void completarAgregaFechasCuandoPasaElTiempoHastaTenerDoceDeHoyEnAdelante() {
        LocalDate primera = HOY.plusDays(3);
        servicio.definirPrimeraFecha(primera);
        LocalDate dentroDeCienDias = HOY.plusDays(100);
        ExperienciaService futuro = new ExperienciaService(db, dao, bitacora, reloj(dentroDeCienDias));

        futuro.completar();

        List<LocalDate> fechas = futuro.fechas();
        assertCadaVeintiochoDias(fechas);
        assertEquals(12, fechas.stream().filter(f -> !f.isBefore(dentroDeCienDias)).count());
        assertEquals(16, fechas.size());
        // las fechas ya pasadas se conservan
        assertEquals(primera, fechas.get(0));
    }

    @Test
    void completarNoDuplicaSiYaHayDoceDeHoyEnAdelante() {
        servicio.definirPrimeraFecha(HOY);

        servicio.completar();
        servicio.completar();

        assertEquals(12, servicio.fechas().size());
    }
}
