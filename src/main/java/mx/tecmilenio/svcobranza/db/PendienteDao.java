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
import mx.tecmilenio.svcobranza.model.EstadoPendiente;
import mx.tecmilenio.svcobranza.model.Pendiente;

public class PendienteDao {

    private static final String SELECT = "SELECT p.id, p.usuario_id, u.nombre, p.categoria, p.fecha_limite, "
            + "p.monto, p.estado, p.motivo, p.movimiento_id "
            + "FROM pendiente p JOIN usuario_atendido u ON u.id = p.usuario_id ";

    private final Database db;

    public PendienteDao(Database db) {
        this.db = db;
    }

    public long insertar(Pendiente p) {
        String sql = "INSERT INTO pendiente (usuario_id, categoria, fecha_limite, monto, estado, motivo, "
                + "movimiento_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.conexion().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            asignar(ps, p);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new AppException("No se pudo guardar el cobro", e);
        }
    }

    public void actualizar(Pendiente p) {
        String sql = "UPDATE pendiente SET usuario_id = ?, categoria = ?, fecha_limite = ?, monto = ?, "
                + "estado = ?, motivo = ?, movimiento_id = ? WHERE id = ?";
        try (PreparedStatement ps = db.conexion().prepareStatement(sql)) {
            asignar(ps, p);
            ps.setLong(8, p.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("No se pudo actualizar el cobro", e);
        }
    }

    public Optional<Pendiente> buscarPorId(long id) {
        List<Pendiente> lista = consultar(SELECT + "WHERE p.id = ?", List.of(id));
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }

    public List<Pendiente> listarTodos() {
        return consultar(SELECT + "ORDER BY p.id", List.of());
    }

    public List<Pendiente> porUsuario(long usuarioId) {
        return consultar(SELECT + "WHERE p.usuario_id = ? ORDER BY p.fecha_limite, p.id", List.of(usuarioId));
    }

    /** Cobros de un usuario, por nombre y sin distinguir mayusculas, ordenados por fecha. */
    public List<Pendiente> porNombreDeUsuario(String nombre) {
        return consultar(SELECT + "WHERE LOWER(u.nombre) = LOWER(?) ORDER BY p.fecha_limite, p.id",
                List.of(nombre.trim()));
    }

    /** Cobros en estado Pendiente con fecha igual o anterior a la dada (incluye los vencidos). */
    public List<Pendiente> pendientesHasta(LocalDate limite) {
        return consultar(SELECT + "WHERE p.estado = 'PENDIENTE' AND p.fecha_limite <= ? "
                + "ORDER BY p.fecha_limite, p.id", List.of(java.sql.Date.valueOf(limite)));
    }

    /** Borra los cobros en estado Pendiente de un usuario; los pagados y condonados se conservan. */
    public void borrarPendientesDeUsuario(long usuarioId) {
        try (PreparedStatement ps = db.conexion()
                .prepareStatement("DELETE FROM pendiente WHERE usuario_id = ? AND estado = 'PENDIENTE'")) {
            ps.setLong(1, usuarioId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("No se pudieron borrar los cobros pendientes", e);
        }
    }

    /**
     * Borra los cobros en estado Pendiente de un usuario con fecha igual o posterior a la dada.
     * Los vencidos (fecha anterior), pagados y condonados se conservan.
     */
    public void borrarPendientesDesde(long usuarioId, LocalDate fecha) {
        try (PreparedStatement ps = db.conexion().prepareStatement(
                "DELETE FROM pendiente WHERE usuario_id = ? AND estado = 'PENDIENTE' AND fecha_limite >= ?")) {
            ps.setLong(1, usuarioId);
            ps.setDate(2, java.sql.Date.valueOf(fecha));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("No se pudieron reemplazar los cobros pendientes", e);
        }
    }

    /** Borra, de todos los usuarios, los cobros Pendiente de una categoria con fecha igual o posterior. */
    public void borrarPendientesDeCategoriaDesde(String categoria, LocalDate fecha) {
        try (PreparedStatement ps = db.conexion().prepareStatement("DELETE FROM pendiente WHERE categoria = ? "
                + "AND estado = 'PENDIENTE' AND fecha_limite >= ?")) {
            ps.setString(1, categoria);
            ps.setDate(2, java.sql.Date.valueOf(fecha));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("No se pudieron reemplazar los cobros pendientes", e);
        }
    }

    public void borrarTodo() {
        try (Statement st = db.conexion().createStatement()) {
            st.execute("DELETE FROM pendiente");
        } catch (SQLException e) {
            throw new AppException("No se pudieron borrar los cobros", e);
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
                    Long movimientoId = rs.wasNull() ? null : movimiento;
                    lista.add(new Pendiente(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                            rs.getDate(5).toLocalDate(), rs.getBigDecimal(6),
                            EstadoPendiente.valueOf(rs.getString(7)), rs.getString(8), movimientoId));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new AppException("No se pudieron consultar los cobros", e);
        }
    }

    private void asignar(PreparedStatement ps, Pendiente p) throws SQLException {
        ps.setLong(1, p.usuarioId());
        ps.setString(2, p.categoria());
        ps.setDate(3, java.sql.Date.valueOf(p.fechaLimite()));
        ps.setBigDecimal(4, p.monto());
        ps.setString(5, p.estado().name());
        ps.setString(6, p.motivo());
        if (p.movimientoId() == null) {
            ps.setNull(7, java.sql.Types.BIGINT);
        } else {
            ps.setLong(7, p.movimientoId());
        }
    }
}
