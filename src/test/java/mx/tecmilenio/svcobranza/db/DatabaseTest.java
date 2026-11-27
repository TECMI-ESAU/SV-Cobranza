package mx.tecmilenio.svcobranza.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;

class DatabaseTest {

    private static final String USUARIO_DE_PRUEBA = "INSERT INTO usuario_atendido (nombre, fecha_ingreso, "
            + "fecha_registro) VALUES ('Persona de prueba', DATE '2026-11-24', DATE '2026-11-24')";

    @Test
    void creaLasTablasDelEsquema() throws Exception {
        try (Database db = Database.enMemoria("esquema")) {
            try (Statement st = db.conexion().createStatement();
                    ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                            + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME IN "
                            + "('MOVIMIENTO','PENDIENTE','CATEGORIA','USUARIO_ATENDIDO','SERVICIO_USUARIO',"
                            + "'EXPERIENCIA_FECHA','CONFIGURACION','BITACORA')")) {
                rs.next();
                assertEquals(8, rs.getInt(1));
            }
        }
    }

    @Test
    void cargaLasCategoriasDeIngresosYEgresos() {
        try (Database db = Database.enMemoria("categorias")) {
            CategoriaDao dao = new CategoriaDao(db);
            List<String> ingresos = dao.listar(TipoMovimiento.INGRESO);
            List<String> egresos = dao.listar(TipoMovimiento.EGRESO);
            assertEquals(List.of("Pago semanal", "Pago mensual", "Médico", "Psicólogo", "Tiendita", "Experiencia",
                    "Otros"), ingresos);
            assertEquals(List.of("Nóminas", "Renta", "Luz", "Internet", "Abasto", "Donación a usuario", "Otros"),
                    egresos);
        }
    }

    @Test
    void lasCategoriasNoSeDuplicanAlAbrirDeNuevo() throws Exception {
        try (Database db = Database.enMemoria("sinduplicar")) {
            // Simula una segunda apertura: el esquema y las categorias se vuelven a preparar
            try (Database otra = Database.enMemoria("sinduplicar")) {
                assertEquals(7, new CategoriaDao(otra).listar(TipoMovimiento.INGRESO).size());
            }
            assertEquals(7, new CategoriaDao(db).listar(TipoMovimiento.INGRESO).size());
        }
    }

    @Test
    void transaccionHaceRollbackSiOcurreUnError() throws Exception {
        try (Database db = Database.enMemoria("rollback")) {
            assertThrows(IllegalStateException.class, () -> db.transaccion(() -> {
                try (Statement st = db.conexion().createStatement()) {
                    st.execute(USUARIO_DE_PRUEBA);
                }
                throw new IllegalStateException("fallo simulado");
            }));
            try (Statement st = db.conexion().createStatement();
                    ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM usuario_atendido")) {
                rs.next();
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    @Test
    void laBaseLocalQuedaCifradaYSeReabreConLaMismaClave(@TempDir Path carpeta) throws Exception {
        try (Database db = Database.local(carpeta)) {
            try (Statement st = db.conexion().createStatement()) {
                st.execute(USUARIO_DE_PRUEBA);
            }
        }
        assertTrue(Files.exists(carpeta.resolve("clave.key")), "debe crearse la clave");
        Path archivo = carpeta.resolve("svcobranza.mv.db");
        assertTrue(Files.exists(archivo));
        // El nombre guardado no debe poder leerse directamente en el archivo cifrado
        String contenido = new String(Files.readAllBytes(archivo), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(contenido.contains("Persona de prueba"));
        assertFalse(contenido.contains("usuario_atendido"));

        try (Database reabierta = Database.local(carpeta);
                Statement st = reabierta.conexion().createStatement();
                ResultSet rs = st.executeQuery("SELECT nombre FROM usuario_atendido")) {
            assertTrue(rs.next());
            assertEquals("Persona de prueba", rs.getString(1));
        }
    }

    @Test
    void conUnaClaveDistintaNoSeAbreLaBase(@TempDir Path carpeta) throws Exception {
        Database.local(carpeta).close();
        Files.writeString(carpeta.resolve("clave.key"), "otraclavecualquiera123");
        assertThrows(AppException.class, () -> Database.local(carpeta));
        assertNotEquals("", Files.readString(carpeta.resolve("clave.key")));
    }

    @Test
    void borrarUnUsuarioBorraSusServiciosYCobrosPeroNoSusIngresos() throws Exception {
        try (Database db = Database.enMemoria("cascada" + System.nanoTime())) {
            try (Statement st = db.conexion().createStatement()) {
                st.execute(USUARIO_DE_PRUEBA);
                st.execute("INSERT INTO servicio_usuario (usuario_id, tipo, monto, recurrencia) "
                        + "SELECT id, 'MEDICO', 100, 'MENSUAL' FROM usuario_atendido");
                st.execute("INSERT INTO pendiente (usuario_id, categoria, fecha_limite, monto) "
                        + "SELECT id, 'Pago mensual', DATE '2026-12-01', 500 FROM usuario_atendido");
                st.execute("INSERT INTO movimiento (tipo, fecha, monto, concepto, categoria, usuario, forma_pago) "
                        + "VALUES ('INGRESO', DATE '2026-11-24', 500, 'Pago', 'Pago mensual', 'Persona de prueba', "
                        + "'EFECTIVO')");
                st.execute("DELETE FROM usuario_atendido");
                assertEquals(0, contar(st, "servicio_usuario"));
                assertEquals(0, contar(st, "pendiente"));
                assertEquals(1, contar(st, "movimiento"));
            }
        }
    }

    private static int contar(Statement st, String tabla) throws Exception {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
