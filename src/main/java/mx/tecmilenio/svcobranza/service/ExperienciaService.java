package mx.tecmilenio.svcobranza.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.db.Database;
import mx.tecmilenio.svcobranza.db.ExperienciaDao;
import mx.tecmilenio.svcobranza.ui.Formatos;

/**
 * Calendario global de experiencia (V2-02): 12 fechas cada 28 dias. La primera la captura la
 * persona encargada; despues la aplicacion lo completa para que siempre haya 12 por delante.
 */
public class ExperienciaService {

    public static final int INTERVALO_DIAS = 28;
    public static final int FECHAS_ADELANTE = 12;

    private final Database db;
    private final ExperienciaDao fechas;
    private final CobroService cobros;
    private final BitacoraService bitacora;
    private final Clock reloj;

    public ExperienciaService(Database db, ExperienciaDao fechas, CobroService cobros, BitacoraService bitacora,
            Clock reloj) {
        this.db = db;
        this.fechas = fechas;
        this.cobros = cobros;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    /** Todas las fechas del calendario, de la mas antigua a la mas reciente. */
    public List<LocalDate> fechas() {
        return fechas.listar();
    }

    /** Sin fecha inicial no se cobra experiencia; la interfaz lo recuerda mientras sea verdad. */
    public boolean sinCalendario() {
        return fechas.listar().isEmpty();
    }

    /** Captura la primera fecha y genera las 12 del calendario. Solo si aun no hay calendario. */
    public void definirPrimeraFecha(LocalDate primera) {
        if (primera == null) {
            throw new AppException("Falta la fecha de experiencia.");
        }
        if (!sinCalendario()) {
            throw new AppException("El calendario de experiencia ya tiene fechas; edita una de ellas.");
        }
        db.transaccion(() -> {
            for (int i = 0; i < FECHAS_ADELANTE; i++) {
                fechas.insertar(primera.plusDays((long) INTERVALO_DIAS * i));
            }
            bitacora.registrar("EXPERIENCIA", "ALTA", null,
                    "Primera fecha del calendario: " + Formatos.fecha(primera));
            completar();
            cobros.reemplazarExperiencia();
            return null;
        });
    }

    /**
     * Cambia una fecha. Las siguientes se recorren a 28 dias de la nueva; las anteriores no
     * cambian. La nueva debe ser posterior a la fecha que la precede en el calendario.
     */
    public void editarFecha(LocalDate actual, LocalDate nueva) {
        if (actual == null || nueva == null) {
            throw new AppException("Falta la fecha de experiencia.");
        }
        List<LocalDate> lista = fechas.listar();
        int posicion = lista.indexOf(actual);
        if (posicion < 0) {
            throw new AppException("La fecha " + Formatos.fecha(actual) + " ya no está en el calendario.");
        }
        if (actual.equals(nueva)) {
            return;
        }
        if (posicion > 0 && !nueva.isAfter(lista.get(posicion - 1))) {
            throw new AppException("La nueva fecha debe ser posterior a la anterior del calendario ("
                    + Formatos.fecha(lista.get(posicion - 1)) + ").");
        }
        int cantidad = lista.size() - posicion;
        db.transaccion(() -> {
            fechas.borrarDesde(actual);
            for (int i = 0; i < cantidad; i++) {
                fechas.insertar(nueva.plusDays((long) INTERVALO_DIAS * i));
            }
            bitacora.registrar("EXPERIENCIA", "EDICION", null,
                    "Fecha " + Formatos.fecha(actual) + " cambiada a " + Formatos.fecha(nueva));
            completar();
            cobros.reemplazarExperiencia();
            return null;
        });
    }

    /**
     * Agrega fechas cada 28 dias hasta que haya 12 de hoy en adelante. No hace nada si el
     * calendario aun no tiene fecha inicial.
     */
    public void completar() {
        List<LocalDate> lista = fechas.listar();
        if (lista.isEmpty()) {
            return;
        }
        LocalDate hoy = LocalDate.now(reloj);
        long adelante = lista.stream().filter(f -> !f.isBefore(hoy)).count();
        LocalDate ultima = lista.get(lista.size() - 1);
        while (adelante < FECHAS_ADELANTE) {
            ultima = ultima.plusDays(INTERVALO_DIAS);
            fechas.insertar(ultima);
            if (!ultima.isBefore(hoy)) {
                adelante++;
            }
        }
    }
}
