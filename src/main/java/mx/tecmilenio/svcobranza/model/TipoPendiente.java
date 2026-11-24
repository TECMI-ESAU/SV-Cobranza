package mx.tecmilenio.svcobranza.model;

public enum TipoPendiente {
    POR_COBRAR("Por cobrar", TipoMovimiento.INGRESO),
    POR_PAGAR("Por pagar", TipoMovimiento.EGRESO);

    private final String etiqueta;
    private final TipoMovimiento movimiento;

    TipoPendiente(String etiqueta, TipoMovimiento movimiento) {
        this.etiqueta = etiqueta;
        this.movimiento = movimiento;
    }

    /** Tipo de movimiento que se genera al marcar el pendiente como pagado. */
    public TipoMovimiento movimiento() {
        return movimiento;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
