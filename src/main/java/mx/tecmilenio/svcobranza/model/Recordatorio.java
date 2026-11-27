package mx.tecmilenio.svcobranza.model;

/**
 * Un cobro que merece aviso. "dias" son los que faltan para su fecha (0 = hoy, negativo = ya
 * venció hace esos días).
 */
public record Recordatorio(Pendiente cobro, ColorAviso color, long dias) {

    public boolean vencido() {
        return color == ColorAviso.ROJO;
    }
}
