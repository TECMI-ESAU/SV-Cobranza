package mx.tecmilenio.svcobranza.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import mx.tecmilenio.svcobranza.model.ColorAviso;

class AvisosTest {

    @Test
    void cadaColorTieneSuEstiloEnLaHojaDeEstilos() throws Exception {
        String css;
        try (var flujo = AvisosTest.class.getResourceAsStream("/estilos.css")) {
            css = new String(flujo.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        for (ColorAviso color : ColorAviso.values()) {
            assertTrue(css.contains("." + Avisos.estilo(color)), "Falta el estilo de " + color);
        }
    }

    @Test
    void describeElPlazoEnPalabras() {
        assertEquals("vence hoy", Avisos.plazo(0));
        assertEquals("vence mañana", Avisos.plazo(1));
        assertEquals("vence en 5 días", Avisos.plazo(5));
        assertEquals("venció ayer", Avisos.plazo(-1));
        assertEquals("venció hace 3 días", Avisos.plazo(-3));
    }
}
