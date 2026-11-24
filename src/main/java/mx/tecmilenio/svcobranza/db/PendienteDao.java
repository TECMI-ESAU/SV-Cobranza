package mx.tecmilenio.svcobranza.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.TipoPendiente;

public class PendienteDao {

    private static final String SELECT = "SELECT id, tipo, fecha_limite, monto, concepto, categoria, "
            + "contraparte, pagado, movimiento_id FROM pendiente ";

    private final Database db;

    public PendienteDao(Database db) {
        this.db = db;
    }

    public long insertar(Pendiente p) {
        String sql = "INSERT INTO pendiente (tipo, fecha_limite, monto, concepto, categoria, contraparte, "
                + "pagado, movimiento_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.conexion().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            asignar(ps, p);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new AppException("No se pudo guardar el pendiente", e);
        }
    }

    public void actualizar(Pendiente p) {
        String sql = "UPDATE pendiente SET tipo = ?, fecha_limite = ?, monto = ?, concepto = ?, categoria = ?, "
                + "contraparte = ?, pagado = ?, movimiento_id = ? WHERE id = ?";
        try (PreparedStatement ps = db.conexion().prepareStatement(sql)) {
            asignar(ps, p);
            ps.setLong(9, p.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("No se pudo actualizar el pendiente", e);
        }
    }

    public Optional<Pendiente> buscarPorId(long id) {
        List<Pendiente> lista = consultar(SELECT + "WHERE id = ?", List.of(id));
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }

    public List<Pendiente> listarTodos() {
        return consultar(SELECT + "ORDER BY id", List.of());
    }

    /** Pendientes sin pagar cuya fecha limite es igual o anterior a la fecha dada. */
    public List<Pendiente> sinPagarHasta(LocalDate limite) {
        return consultar(SELECT + "WHERE pagado = FALSE AND fecha_limite <= ? ORDER BY fecha_limite, id",
                List.of(java.sql.Date.valueOf(limite)));
    }

    /** Pendientes de una categoria, pagados o no, para saber quien ya pago y quien no. */
    public List<Pendiente> porCategoria(String categoria) {
        return consultar(SELECT + "WHERE categoria = ? ORDER BY pagado, fecha_limite, id", List.of(categoria));
    }

    public List<Pendiente> porContraparte(String nombre) {
        return consultar(SELECT + "WHERE LOWER(contraparte) = LOWER(?) ORDER BY fecha_limite, id",
                List.of(nombre.trim()));
    }

    public void borrarTodo() {
        try (Statement st = db.conexion().createStatement()) {
            st.execute("DELETE FROM pendiente");
        } catch (SQLException e) {
            throw new AppException("No se pudieron borrar los pendientes", e);
        }
    }

    private List<Pendiente> consultar(String sql, List<Object> parametros) {
        try (PreparedStatement ps = db.conexion().prepareStatement(sql)) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Pendiente> lista = new ArrayList<>();
                while (rs.next()) {
                    long movimiento = rs.getLong(9);
                    lista.add(new Pendiente(rs.getLong(1), TipoPendiente.valueOf(rs.getString(2)),
                            rs.getDate(3).toLocalDate(), rs.getBigDecimal(4), rs.getString(5),
                            rs.getString(6), rs.getString(7), rs.getBoolean(8),
                            rs.wasNull() ? null : movimiento));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new AppException("No se pudieron consultar los pendientes", e);
        }
    }

    private void asignar(PreparedStatement ps, Pendiente p) throws SQLException {
        ps.setString(1, p.tipo().name());
        ps.setDate(2, java.sql.Date.valueOf(p.fechaLimite()));
        ps.setBigDecimal(3, p.monto());
        ps.setString(4, p.concepto());
        ps.setString(5, p.categoria());
        ps.setString(6, p.contraparte());
        ps.setBoolean(7, p.pagado());
        if (p.movimientoId() == null) {
            ps.setNull(8, java.sql.Types.BIGINT);
        } else {
            ps.setLong(8, p.movimientoId());
        }
    }
}
