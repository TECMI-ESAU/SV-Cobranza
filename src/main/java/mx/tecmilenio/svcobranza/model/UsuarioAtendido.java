package mx.tecmilenio.svcobranza.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Usuario atendido con los datos que determinan sus cobros. Un id de 0 indica que aun no se
 * guarda. "fechaBase" aplica a la estancia semanal y "diaMes" a la mensual. El monto de
 * tiendita es solo de referencia: nunca genera cobros.
 */
public record UsuarioAtendido(long id, String nombre, LocalDate fechaIngreso, LocalDate fechaRegistro,
        String esquema, boolean becado, Frecuencia frecuenciaEstancia, BigDecimal montoEstancia,
        LocalDate fechaBase, Integer diaMes, boolean experiencia, BigDecimal montoExperiencia,
        BigDecimal montoTiendita, List<Servicio> servicios) {

    public UsuarioAtendido {
        servicios = servicios == null ? List.of() : List.copyOf(servicios);
    }

    public UsuarioAtendido conId(long nuevoId) {
        return new UsuarioAtendido(nuevoId, nombre, fechaIngreso, fechaRegistro, esquema, becado,
                frecuenciaEstancia, montoEstancia, fechaBase, diaMes, experiencia, montoExperiencia,
                montoTiendita, servicios);
    }

    public UsuarioAtendido conFechaRegistro(LocalDate fecha) {
        return new UsuarioAtendido(id, nombre, fechaIngreso, fecha, esquema, becado, frecuenciaEstancia,
                montoEstancia, fechaBase, diaMes, experiencia, montoExperiencia, montoTiendita, servicios);
    }

    public Servicio servicio(TipoServicio tipo) {
        return servicios.stream().filter(s -> s.tipo() == tipo).findFirst().orElse(null);
    }

    @Override
    public String toString() {
        return nombre;
    }
}
