package mx.tecmilenio.svcobranza.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;

public class CategoriaDao {

    private final Database db;

    public CategoriaDao(Database db) {
        this.db = db;
    }

    public List<String> listar(TipoMovimiento tipo) {
        String sql = "SELECT nombre FROM categoria WHERE tipo = ? ORDER BY id";
        try (PreparedStatement ps = db.conexion().prepareStatement(sql)) {
            ps.setString(1, tipo.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<String> categorias = new ArrayList<>();
                while (rs.next()) {
                    categorias.add(rs.getString(1));
                }
                return categorias;
            }
        } catch (SQLException e) {
            throw new AppException("No se pudieron leer las categorías", e);
        }
    }
}
