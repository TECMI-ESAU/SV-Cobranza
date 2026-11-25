package mx.tecmilenio.svcobranza.model;

public enum FormaPago {
    DEPOSITO("Depósito"),
    TRANSFERENCIA("Transferencia"),
    EFECTIVO("Efectivo");

    private final String etiqueta;

    FormaPago(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
