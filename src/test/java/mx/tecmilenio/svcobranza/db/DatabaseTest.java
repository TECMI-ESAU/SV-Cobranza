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

    @Test
    void creaLasTablasDelEsquema() throws Exception {
        try (Database db = Database.enMemoria("esquema")) {
            try (Statement st = db.conexion().createStatement();
                    ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                            + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME IN "
                            + "('MOVIMIENTO','PENDIENTE','CATEGORIA','USUARIO_ATENDIDO','BITACORA')")) {
                rs.next();
                assertEquals(5, rs.getInt(1));
            }
        }
    }

    @Test
    void cargaLasCategoriasDeIngresosYEgresos() {
        try (Database db = Database.enMemoria("categorias")) {
            CategoriaDao dao = new CategoriaDao(db);
            List<String> ingresos = dao.listar(TipoMovimiento.INGRESO);
            List<String> egresos = dao.listar(TipoMovimiento.EGRESO);
            assertEquals(6, ingresos.size());
            assertTrue(ingresos.contains("Tiendita"));
            assertTrue(ingresos.contains("Consulta médica o psicológica"));
            assertEquals(6, egresos.size());
            assertTrue(egresos.contains("Nóminas"));
        }
    }

    @Test
    void lasCategoriasNoSeDuplicanAlAbrirDeNuevo() throws Exception {
        try (Database db = Database.enMemoria("sinduplicar")) {
            // Simula una segunda apertura: el esquema y las categorias se vuelven a preparar
            try (Database otra = Database.enMemoria("sinduplicar")) {
                assertEquals(6, new CategoriaDao(otra).listar(TipoMovimiento.INGRESO).size());
            }
            assertEquals(6, new CategoriaDao(db).listar(TipoMovimiento.INGRESO).size());
        }
    }

    @Test
    void transaccionHaceRollbackSiOcurreUnError() throws Exception {
        try (Database db = Database.enMemoria("rollback")) {
            assertThrows(IllegalStateException.class, () -> db.transaccion(() -> {
                try (Statement st = db.conexion().createStatement()) {
                    st.execute("INSERT INTO usuario_atendido (nombre) VALUES ('Persona de prueba')");
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
                st.execute("INSERT INTO usuario_atendido (nombre) VALUES ('Persona de prueba')");
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
}
