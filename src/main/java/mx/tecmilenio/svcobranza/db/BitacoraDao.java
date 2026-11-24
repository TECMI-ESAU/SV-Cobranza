package mx.tecmilenio.svcobranza.db;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.model.EntradaBitacora;

public class BitacoraDao {

    private final Database db;

    public BitacoraDao(Database db) {
        this.db = db;
    }

    public void registrar(LocalDateTime fechaHora, String tipo, String accion, BigDecimal monto,
            String detalle) {
        String sql = "INSERT INTO bitacora (fecha_hora, tipo, accion, monto, detalle) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.conexion().prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(fechaHora));
            ps.setString(2, tipo);
            ps.setString(3, accion);
            ps.setBigDecimal(4, monto);
            ps.setString(5, detalle);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("No se pudo escribir en la bitácora", e);
        }
    }

    public void borrarTodo() {
        try (java.sql.Statement st = db.conexion().createStatement()) {
            st.execute("DELETE FROM bitacora");
        } catch (SQLException e) {
            throw new AppException("No se pudo vaciar la bitácora", e);
        }
    }

    /** Entradas de la bitacora, de la mas reciente a la mas antigua. */
    public List<EntradaBitacora> listar() {
        String sql = "SELECT id, fecha_hora, tipo, accion, monto, detalle FROM bitacora ORDER BY fecha_hora DESC, id DESC";
        try (PreparedStatement ps = db.conexion().prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<EntradaBitacora> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new EntradaBitacora(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime(),
                        rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6)));
            }
            return lista;
        } catch (SQLException e) {
            throw new AppException("No se pudo leer la bitácora", e);
        }
    }
}
