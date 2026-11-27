package mx.tecmilenio.svcobranza.model;

public enum EstadoPendiente {
    PENDIENTE("Pendiente"),
    PAGADO("Pagado"),
    CONDONADO("Condonado");

    private final String etiqueta;

    EstadoPendiente(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
