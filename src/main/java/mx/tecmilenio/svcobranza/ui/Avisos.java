package mx.tecmilenio.svcobranza.ui;

import mx.tecmilenio.svcobranza.model.ColorAviso;

/**
 * Textos y estilos de los recordatorios. No usa JavaFX, por lo que se prueba en el CI sin pantalla.
 */
public final class Avisos {

    private Avisos() {
    }

    /** Clase CSS (en estilos.css) con el color de cada tipo de aviso. */
    public static String estilo(ColorAviso color) {
        return switch (color) {
            case DORADO -> "aviso-experiencia";
            case MORADO -> "aviso-medico";
            case MORADO_CLARO -> "aviso-psicologo";
            case VERDE -> "aviso-mensual";
            case AZUL -> "aviso-semanal";
            case ROJO -> "aviso-vencido";
        };
    }

    /** Cuando vence un cobro, segun los dias que faltan (negativo = ya vencio). */
    public static String plazo(long dias) {
        if (dias < -1) {
            return "venció hace " + (-dias) + " días";
        }
        if (dias == -1) {
            return "venció ayer";
        }
        if (dias == 0) {
            return "vence hoy";
        }
        if (dias == 1) {
            return "vence mañana";
        }
        return "vence en " + dias + " días";
    }
}
