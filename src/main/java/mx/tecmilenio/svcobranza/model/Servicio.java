package mx.tecmilenio.svcobranza.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Servicio medico o psicologico de un usuario. Sin fecha de inicio no genera cobros. */
public record Servicio(TipoServicio tipo, BigDecimal monto, LocalDate fechaInicio, Frecuencia recurrencia) {
}
