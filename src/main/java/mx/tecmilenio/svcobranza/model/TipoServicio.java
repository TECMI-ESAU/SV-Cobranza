package mx.tecmilenio.svcobranza.model;

/** Servicios que se cobran aparte de la estancia. */
public enum TipoServicio {
    MEDICO(Categorias.MEDICO),
    PSICOLOGO(Categorias.PSICOLOGO);

    private final String categoria;

    TipoServicio(String categoria) {
        this.categoria = categoria;
    }

    /** Categoria de ingreso con la que se registra su pago. */
    public String categoria() {
        return categoria;
    }

    @Override
    public String toString() {
        return categoria;
    }
}
