package mx.tecmilenio.svcobranza;

/**
 * Error de la aplicacion con un mensaje pensado para mostrarse al usuario.
 */
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AppException(String mensaje) {
        super(mensaje);
    }

    public AppException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
