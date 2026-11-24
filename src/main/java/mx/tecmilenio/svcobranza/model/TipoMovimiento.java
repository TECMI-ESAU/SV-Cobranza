package mx.tecmilenio.svcobranza.model;

public enum TipoMovimiento {
    INGRESO("Ingreso"),
    EGRESO("Egreso");

    private final String etiqueta;

    TipoMovimiento(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
