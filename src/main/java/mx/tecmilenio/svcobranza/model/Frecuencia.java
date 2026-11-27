package mx.tecmilenio.svcobranza.model;

/** Cada cuanto se cobra la estancia o un servicio. */
public enum Frecuencia {
    SEMANAL("Semanal"),
    MENSUAL("Mensual");

    private final String etiqueta;

    Frecuencia(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
