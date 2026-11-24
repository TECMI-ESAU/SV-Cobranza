package mx.tecmilenio.svcobranza;

import java.nio.file.Path;
import java.time.Clock;

import mx.tecmilenio.svcobranza.db.BitacoraDao;
import mx.tecmilenio.svcobranza.db.CategoriaDao;
import mx.tecmilenio.svcobranza.db.Database;
import mx.tecmilenio.svcobranza.db.MovimientoDao;
import mx.tecmilenio.svcobranza.db.PendienteDao;
import mx.tecmilenio.svcobranza.db.UsuarioDao;
import mx.tecmilenio.svcobranza.service.BalanceService;
import mx.tecmilenio.svcobranza.service.BitacoraService;
import mx.tecmilenio.svcobranza.service.ExcelService;
import mx.tecmilenio.svcobranza.service.MovimientoService;
import mx.tecmilenio.svcobranza.service.PendienteService;
import mx.tecmilenio.svcobranza.service.RespaldoService;

/** Une la base de datos y los servicios. Es el unico lugar donde se crean las dependencias. */
public final class Aplicacion implements AutoCloseable {

    private final Database db;
    private final UsuarioDao usuarios;
    private final MovimientoService movimientos;
    private final PendienteService pendientes;
    private final BalanceService balance;
    private final BitacoraService bitacora;
    private final RespaldoService respaldo;
    private final ExcelService excel = new ExcelService();

    public Aplicacion(Database db, Clock reloj) {
        this.db = db;
        this.usuarios = new UsuarioDao(db);
        BitacoraDao bitacoraDao = new BitacoraDao(db);
        MovimientoDao movimientoDao = new MovimientoDao(db, usuarios);
        PendienteDao pendienteDao = new PendienteDao(db);
        this.bitacora = new BitacoraService(bitacoraDao, reloj);
        this.movimientos = new MovimientoService(db, movimientoDao, new CategoriaDao(db), bitacora);
        this.pendientes = new PendienteService(db, pendienteDao, movimientos, bitacora, reloj);
        this.balance = new BalanceService(movimientoDao, pendienteDao, reloj);
        this.respaldo = new RespaldoService(db, movimientoDao, pendienteDao, bitacoraDao, bitacora);
    }

    /** Aplicacion con la base de datos cifrada en la carpeta del usuario. */
    public static Aplicacion enCarpeta(Path carpeta) {
        return new Aplicacion(Database.local(carpeta), Clock.systemDefaultZone());
    }

    public MovimientoService movimientos() {
        return movimientos;
    }

    public PendienteService pendientes() {
        return pendientes;
    }

    public BalanceService balance() {
        return balance;
    }

    public BitacoraService bitacora() {
        return bitacora;
    }

    public RespaldoService respaldo() {
        return respaldo;
    }

    public ExcelService excel() {
        return excel;
    }

    public UsuarioDao usuarios() {
        return usuarios;
    }

    @Override
    public void close() {
        db.close();
    }
}
