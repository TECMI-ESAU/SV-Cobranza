package mx.tecmilenio.svcobranza.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.db.ConfiguracionDao;
import mx.tecmilenio.svcobranza.db.PendienteDao;
import mx.tecmilenio.svcobranza.db.UsuarioDao;
import mx.tecmilenio.svcobranza.model.Categorias;
import mx.tecmilenio.svcobranza.model.ColorAviso;
import mx.tecmilenio.svcobranza.model.EstadoPendiente;
import mx.tecmilenio.svcobranza.model.Frecuencia;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.Recordatorio;
import mx.tecmilenio.svcobranza.model.Servicio;
import mx.tecmilenio.svcobranza.model.TipoServicio;
import mx.tecmilenio.svcobranza.model.UsuarioAtendido;

/**
 * Recordatorios de cobro (V2-05). Se muestran en una ventana una vez al día al abrir la
 * aplicación; la pestaña Cobros pendientes tiene un botón para consultarlos de nuevo.
 *
 * <p>Un cobro sin pagar aparece cuando:
 * <ul>
 * <li>ya venció (fecha anterior a hoy), en rojo, hasta que se pague o se condone;</li>
 * <li>es de Experiencia y faltan los días de aviso o menos (7 por defecto), en dorado;</li>
 * <li>es semanal (estancia, Médico o Psicólogo) y su fecha es hoy, en azul (morado en servicios);</li>
 * <li>es mensual (estancia, Médico o Psicólogo) y faltan los días de aviso o menos (3 por defecto),
 *     en verde (morado en servicios).</li>
 * </ul>
 * Los cobros condonados o pagados nunca generan recordatorio, y la tiendita no genera cobros.
 */
public class RecordatorioService {

    public static final String CLAVE_DIAS_EXPERIENCIA = "dias_aviso_experiencia";
    public static final String CLAVE_DIAS_MENSUAL = "dias_aviso_mensual";
    public static final String CLAVE_ULTIMO_AVISO = "ultimo_aviso";

    public static final int DIAS_EXPERIENCIA_POR_DEFECTO = 7;
    public static final int DIAS_MENSUAL_POR_DEFECTO = 3;
    /** Los cobros solo se generan 30 días hacia adelante, así que un aviso más lejano no tendría efecto. */
    public static final int DIAS_MAXIMOS = CobroService.HORIZONTE_DIAS;

    private final UsuarioDao usuarios;
    private final PendienteDao pendientes;
    private final ConfiguracionDao configuracion;
    private final Clock reloj;

    public RecordatorioService(UsuarioDao usuarios, PendienteDao pendientes, ConfiguracionDao configuracion,
            Clock reloj) {
        this.usuarios = usuarios;
        this.pendientes = pendientes;
        this.configuracion = configuracion;
        this.reloj = reloj;
    }

    // ---------- días de aviso ----------

    public int diasAvisoExperiencia() {
        return leerDias(CLAVE_DIAS_EXPERIENCIA, DIAS_EXPERIENCIA_POR_DEFECTO);
    }

    public int diasAvisoMensual() {
        return leerDias(CLAVE_DIAS_MENSUAL, DIAS_MENSUAL_POR_DEFECTO);
    }

    public void definirDiasAviso(int experiencia, int mensual) {
        validarDias(experiencia, "Experiencia");
        validarDias(mensual, "Pago mensual");
        configuracion.guardar(CLAVE_DIAS_EXPERIENCIA, String.valueOf(experiencia));
        configuracion.guardar(CLAVE_DIAS_MENSUAL, String.valueOf(mensual));
    }

    private static void validarDias(int dias, String concepto) {
        if (dias < 0 || dias > DIAS_MAXIMOS) {
            throw new AppException("Los días de aviso de " + concepto + " deben estar entre 0 y " + DIAS_MAXIMOS
                    + ".");
        }
    }

    private int leerDias(String clave, int porDefecto) {
        Optional<String> guardado = configuracion.obtener(clave);
        if (guardado.isEmpty()) {
            return porDefecto;
        }
        try {
            int dias = Integer.parseInt(guardado.get().trim());
            return dias < 0 ? porDefecto : dias;
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    // ---------- aviso del día ----------

    /** Todos los recordatorios vigentes hoy: primero los vencidos, luego por fecha. */
    public List<Recordatorio> recordatorios() {
        LocalDate hoy = LocalDate.now(reloj);
        int diasExperiencia = diasAvisoExperiencia();
        int diasMensual = diasAvisoMensual();
        Map<Long, UsuarioAtendido> cacheUsuarios = new HashMap<>();
        List<Recordatorio> lista = new ArrayList<>();
        for (Pendiente p : pendientes.pendientesHasta(hoy.plusDays(CobroService.HORIZONTE_DIAS))) {
            if (p.estado() != EstadoPendiente.PENDIENTE) {
                continue;
            }
            long dias = ChronoUnit.DAYS.between(hoy, p.fechaLimite());
            if (dias < 0) {
                lista.add(new Recordatorio(p, ColorAviso.ROJO, dias));
                continue;
            }
            Frecuencia recurrencia = recurrencia(p, cacheUsuarios);
            boolean avisar;
            if (Categorias.EXPERIENCIA.equals(p.categoria())) {
                avisar = dias <= diasExperiencia;
            } else if (recurrencia == Frecuencia.SEMANAL) {
                avisar = dias == 0;
            } else {
                avisar = dias <= diasMensual;
            }
            if (avisar) {
                lista.add(new Recordatorio(p, color(p, recurrencia), dias));
            }
        }
        lista.sort(Comparator.comparing((Recordatorio r) -> !r.vencido())
                .thenComparing(r -> r.cobro().fechaLimite())
                .thenComparing(r -> r.cobro().usuario().toLowerCase())
                .thenComparingLong(r -> r.cobro().id()));
        return lista;
    }

    /**
     * Recordatorios que deben mostrarse al abrir la aplicación: vacío si ya se mostraron hoy o si
     * no hay nada que avisar. Al devolver recordatorios queda registrado que hoy ya se mostraron.
     */
    public List<Recordatorio> avisoDelDia() {
        LocalDate hoy = LocalDate.now(reloj);
        if (yaSeMostroHoy(hoy)) {
            return List.of();
        }
        List<Recordatorio> lista = recordatorios();
        if (!lista.isEmpty()) {
            configuracion.guardar(CLAVE_ULTIMO_AVISO, hoy.toString());
        }
        return lista;
    }

    private boolean yaSeMostroHoy(LocalDate hoy) {
        return configuracion.obtener(CLAVE_ULTIMO_AVISO).map(v -> v.equals(hoy.toString())).orElse(false);
    }

    // ---------- reglas por tipo de cobro ----------

    /** Recurrencia del cobro: la de la estancia, o la del servicio (Médico o Psicólogo) del usuario. */
    private Frecuencia recurrencia(Pendiente p, Map<Long, UsuarioAtendido> cache) {
        if (Categorias.PAGO_SEMANAL.equals(p.categoria())) {
            return Frecuencia.SEMANAL;
        }
        if (Categorias.PAGO_MENSUAL.equals(p.categoria())) {
            return Frecuencia.MENSUAL;
        }
        TipoServicio tipo = Categorias.MEDICO.equals(p.categoria()) ? TipoServicio.MEDICO
                : Categorias.PSICOLOGO.equals(p.categoria()) ? TipoServicio.PSICOLOGO : null;
        if (tipo == null) {
            return null;
        }
        UsuarioAtendido u = cache.computeIfAbsent(p.usuarioId(), id -> usuarios.buscarPorId(id).orElse(null));
        Servicio s = u == null ? null : u.servicio(tipo);
        return s == null ? null : s.recurrencia();
    }

    private static ColorAviso color(Pendiente p, Frecuencia recurrencia) {
        return switch (p.categoria()) {
            case Categorias.EXPERIENCIA -> ColorAviso.DORADO;
            case Categorias.MEDICO -> ColorAviso.MORADO;
            case Categorias.PSICOLOGO -> ColorAviso.MORADO_CLARO;
            default -> recurrencia == Frecuencia.SEMANAL ? ColorAviso.AZUL : ColorAviso.VERDE;
        };
    }
}
