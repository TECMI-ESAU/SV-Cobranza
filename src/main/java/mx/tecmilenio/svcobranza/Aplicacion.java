package mx.tecmilenio.svcobranza;

import java.nio.file.Path;
import java.time.Clock;

import mx.tecmilenio.svcobranza.db.BitacoraDao;
import mx.tecmilenio.svcobranza.db.CategoriaDao;
import mx.tecmilenio.svcobranza.db.ConfiguracionDao;
import mx.tecmilenio.svcobranza.db.Database;
import mx.tecmilenio.svcobranza.db.ExperienciaDao;
import mx.tecmilenio.svcobranza.db.MovimientoDao;
import mx.tecmilenio.svcobranza.db.PendienteDao;
import mx.tecmilenio.svcobranza.db.UsuarioDao;
import mx.tecmilenio.svcobranza.service.BalanceService;
import mx.tecmilenio.svcobranza.service.BitacoraService;
import mx.tecmilenio.svcobranza.service.ExcelService;
import mx.tecmilenio.svcobranza.service.ExperienciaService;
import mx.tecmilenio.svcobranza.service.MovimientoService;
import mx.tecmilenio.svcobranza.service.RespaldoService;
import mx.tecmilenio.svcobranza.service.UsuarioService;

/** Une la base de datos y los servicios. Es el unico lugar donde se crean las dependencias. */
public final class Aplicacion implements AutoCloseable {

    private final Database db;
    private final UsuarioService usuarios;
    private final ExperienciaService experiencia;
    private final MovimientoService movimientos;
    private final BalanceService balance;
    private final BitacoraService bitacora;
    private final RespaldoService respaldo;
    private final ExcelService excel = new ExcelService();

    public Aplicacion(Database db, Clock reloj) {
        this.db = db;
        UsuarioDao usuarioDao = new UsuarioDao(db);
        BitacoraDao bitacoraDao = new BitacoraDao(db);
        MovimientoDao movimientoDao = new MovimientoDao(db);
        PendienteDao pendienteDao = new PendienteDao(db);
        ExperienciaDao experienciaDao = new ExperienciaDao(db);
        ConfiguracionDao configuracionDao = new ConfiguracionDao(db);
        this.bitacora = new BitacoraService(bitacoraDao, reloj);
        this.usuarios = new UsuarioService(db, usuarioDao, movimientoDao, pendienteDao, bitacora, reloj);
        this.experiencia = new ExperienciaService(db, experienciaDao, bitacora, reloj);
        this.movimientos = new MovimientoService(db, movimientoDao, new CategoriaDao(db), usuarioDao, bitacora);
        this.balance = new BalanceService(movimientoDao, pendienteDao, reloj);
        this.respaldo = new RespaldoService(db, usuarioDao, movimientoDao, pendienteDao, experienciaDao,
                configuracionDao, bitacoraDao, bitacora);
    }

    /** Aplicacion con la base de datos cifrada en la carpeta del usuario. */
    public static Aplicacion enCarpeta(Path carpeta) {
        return new Aplicacion(Database.local(carpeta), Clock.systemDefaultZone());
    }

    public UsuarioService usuarios() {
        return usuarios;
    }

    public ExperienciaService experiencia() {
        return experiencia;
    }

    public MovimientoService movimientos() {
        return movimientos;
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

    @Override
    public void close() {
        db.close();
    }
}
