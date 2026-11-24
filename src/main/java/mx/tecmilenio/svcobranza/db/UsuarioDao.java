package mx.tecmilenio.svcobranza.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import mx.tecmilenio.svcobranza.AppException;

public class UsuarioDao {

    private final Database db;

    public UsuarioDao(Database db) {
        this.db = db;
    }

    /** Devuelve el id del usuario atendido; si no existe lo crea. */
    public long obtenerOCrearId(String nombre) {
        String limpio = nombre.trim();
        try {
            try (PreparedStatement ps = db.conexion()
                    .prepareStatement("SELECT id FROM usuario_atendido WHERE LOWER(nombre) = LOWER(?)")) {
                ps.setString(1, limpio);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
            try (PreparedStatement ps = db.conexion().prepareStatement(
                    "INSERT INTO usuario_atendido (nombre) VALUES (?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, limpio);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new AppException("No se pudo guardar el usuario atendido", e);
        }
    }

    public List<String> listarNombres() {
        try (PreparedStatement ps = db.conexion()
                .prepareStatement("SELECT nombre FROM usuario_atendido ORDER BY nombre");
                ResultSet rs = ps.executeQuery()) {
            List<String> nombres = new ArrayList<>();
            while (rs.next()) {
                nombres.add(rs.getString(1));
            }
            return nombres;
        } catch (SQLException e) {
            throw new AppException("No se pudieron leer los usuarios atendidos", e);
        }
    }
}
