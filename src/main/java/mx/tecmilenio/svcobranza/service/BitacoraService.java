package mx.tecmilenio.svcobranza.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import mx.tecmilenio.svcobranza.db.BitacoraDao;
import mx.tecmilenio.svcobranza.model.EntradaBitacora;

/** RF-BIT-01 y RF-BIT-02: registro y consulta de la bitacora de movimientos. */
public class BitacoraService {

    private final BitacoraDao dao;
    private final Clock reloj;

    public BitacoraService(BitacoraDao dao, Clock reloj) {
        this.dao = dao;
        this.reloj = reloj;
    }

    public void registrar(String tipo, String accion, BigDecimal monto, String detalle) {
        dao.registrar(LocalDateTime.now(reloj), tipo, accion, monto, detalle);
    }

    public List<EntradaBitacora> consultar() {
        return dao.listar();
    }
}
