package mx.tecmilenio.svcobranza.service;

import java.util.List;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.model.Movimiento;

/** RF-TRA-01: se detecto un movimiento parecido; el usuario debe confirmar si lo quiere guardar. */
public class PosibleDuplicadoException extends AppException {

    private static final long serialVersionUID = 1L;

    private final transient List<Movimiento> parecidos;

    public PosibleDuplicadoException(List<Movimiento> parecidos) {
        super("Ya existe un movimiento con la misma fecha, tipo y monto. ¿Es un duplicado?");
        this.parecidos = parecidos;
    }

    public List<Movimiento> parecidos() {
        return parecidos;
    }
}
