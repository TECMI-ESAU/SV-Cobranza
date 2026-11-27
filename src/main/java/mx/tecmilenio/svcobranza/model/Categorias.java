package mx.tecmilenio.svcobranza.model;

import java.util.List;

/** Nombres de las categorias de ingresos y egresos (v2.0). */
public final class Categorias {

    public static final String PAGO_SEMANAL = "Pago semanal";
    public static final String PAGO_MENSUAL = "Pago mensual";
    public static final String MEDICO = "Médico";
    public static final String PSICOLOGO = "Psicólogo";
    public static final String TIENDITA = "Tiendita";
    public static final String EXPERIENCIA = "Experiencia";
    public static final String OTROS = "Otros";

    public static final String NOMINAS = "Nóminas";
    public static final String RENTA = "Renta";
    public static final String LUZ = "Luz";
    public static final String INTERNET = "Internet";
    public static final String ABASTO = "Abasto";
    public static final String DONACION = "Donación a usuario";

    public static final List<String> INGRESO = List.of(
            PAGO_SEMANAL, PAGO_MENSUAL, MEDICO, PSICOLOGO, TIENDITA, EXPERIENCIA, OTROS);
    public static final List<String> EGRESO = List.of(
            NOMINAS, RENTA, LUZ, INTERNET, ABASTO, DONACION, OTROS);

    private Categorias() {
    }
}
