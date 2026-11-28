package mx.tecmilenio.svcobranza.ui;

import java.util.ArrayList;
import java.util.List;

import mx.tecmilenio.svcobranza.Aplicacion;

/**
 * Lo que comparten las pestañas: la aplicacion y un aviso de "cambiaron los datos" para que
 * cada pestaña se refresque (por ejemplo, la lista de usuarios tras dar de alta uno).
 */
public final class Contexto {

    private final Aplicacion aplicacion;
    private final List<Runnable> oyentes = new ArrayList<>();

    public Contexto(Aplicacion aplicacion) {
        this.aplicacion = aplicacion;
    }

    public Aplicacion app() {
        return aplicacion;
    }

    /** Registra algo que debe ejecutarse cada vez que cambian los datos. */
    public void alCambiar(Runnable oyente) {
        oyentes.add(oyente);
    }

    /** Avisa a todas las pestañas que los datos cambiaron. */
    public void cambio() {
        for (Runnable oyente : List.copyOf(oyentes)) {
            oyente.run();
        }
    }
}
