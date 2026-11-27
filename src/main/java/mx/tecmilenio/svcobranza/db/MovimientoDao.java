package mx.tecmilenio.svcobranza.db;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.model.FiltroMovimiento;
import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Movimiento;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;

public class MovimientoDao {

    private static final String SELECT = "SELECT m.id, m.tipo, m.fecha, m.monto, m.concepto, m.categoria, "
            + "m.usuario, m.forma_pago, m.cancelado FROM movimiento m ";

    private final Database db;

    public MovimientoDao(Database db) {
        this.db = db;
    }

    /** Inserta el movimiento y devuelve su id. */
    public long insertar(Movimiento m) {
        String sql = "INSERT INTO movimiento (tipo, fecha, monto, concepto, categoria, usuario, "
                + "forma_pago, cancelado) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.conexion().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            asignar(ps, m);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new AppException("No se pudo guardar el movimiento", e);
        }
    }

    public void actualizar(Movimiento m) {
        String sql = "UPDATE movimiento SET tipo = ?, fecha = ?, monto = ?, concepto = ?, categoria = ?, "
                + "usuario = ?, forma_pago = ?, cancelado = ? WHERE id = ?";
        try (PreparedStatement ps = db.conexion().prepareStatement(sql)) {
            asignar(ps, m);
            ps.setLong(9, m.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("No se pudo actualizar el movimiento", e);
        }
    }

    public Optional<Movimiento> buscarPorId(long id) {
        try (PreparedStatement ps = db.conexion().prepareStatement(SELECT + "WHERE m.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(leer(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AppException("No se pudo leer el movimiento", e);
        }
    }

    public List<Movimiento> buscar(FiltroMovimiento f) {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1 = 1 ");
        List<Object> parametros = new ArrayList<>();
        if (f.tipo() != null) {
            sql.append("AND m.tipo = ? ");
            parametros.add(f.tipo().name());
        }
        if (!f.incluirCancelados()) {
            sql.append("AND m.cancelado = FALSE ");
        }
        if (hayTexto(f.persona())) {
            sql.append("AND LOWER(m.usuario) LIKE ? ");
            parametros.add("%" + f.persona().trim().toLowerCase() + "%");
        }
        if (hayTexto(f.concepto())) {
            sql.append("AND LOWER(m.concepto) LIKE ? ");
            parametros.add("%" + f.concepto().trim().toLowerCase() + "%");
        }
        if (f.desde() != null) {
            sql.append("AND m.fecha >= ? ");
            parametros.add(java.sql.Date.valueOf(f.desde()));
        }
        if (f.hasta() != null) {
            sql.append("AND m.fecha <= ? ");
            parametros.add(java.sql.Date.valueOf(f.hasta()));
        }
        if (f.montoMin() != null) {
            sql.append("AND m.monto >= ? ");
            parametros.add(f.montoMin());
        }
        if (f.montoMax() != null) {
            sql.append("AND m.monto <= ? ");
            parametros.add(f.montoMax());
        }
        sql.append("ORDER BY m.fecha DESC, m.id DESC");
        return consultar(sql.toString(), parametros);
    }

    /** Todos los movimientos, incluidos los cancelados (para respaldos). */
    public List<Movimiento> listarTodos() {
        return consultar(SELECT + "ORDER BY m.id", List.of());
    }

    /** Movimientos de un usuario atendido, sin los cancelados, del mas antiguo al mas reciente. */
    public List<Movimiento> listarPorUsuario(String nombre) {
        return consultar(SELECT + "WHERE LOWER(m.usuario) = LOWER(?) AND m.cancelado = FALSE "
                + "ORDER BY m.fecha, m.id", List.of(nombre.trim()));
    }

    /** Cambia el nombre guardado como texto en los movimientos de un usuario que se renombra. */
    public void renombrarUsuario(String anterior, String nuevo) {
        try (PreparedStatement ps = db.conexion()
                .prepareStatement("UPDATE movimiento SET usuario = ? WHERE usuario = ?")) {
            ps.setString(1, nuevo);
            ps.setString(2, anterior);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("No se pudo actualizar el nombre en los movimientos", e);
        }
    }

    /**
     * Movimientos activos del mismo tipo, fecha y monto que podrian ser un duplicado.
     * Si la persona es null no se compara.
     */
    public List<Movimiento> posiblesDuplicados(Movimiento m) {
        String sql = SELECT + "WHERE m.tipo = ? AND m.fecha = ? AND m.monto = ? AND m.cancelado = FALSE "
                + "AND m.id <> ? ORDER BY m.id";
        return consultar(sql, List.of(m.tipo().name(), java.sql.Date.valueOf(m.fecha()), m.monto(), m.id()));
    }

    /** Suma de montos activos por categoria entre dos fechas (ambas incluidas). */
    public Map<String, BigDecimal> totalesPorCategoria(TipoMovimiento tipo, LocalDate desde, LocalDate hasta) {
        String sql = "SELECT categoria, SUM(monto) FROM movimiento WHERE tipo = ? AND cancelado = FALSE "
                + "AND fecha >= ? AND fecha <= ? GROUP BY categoria ORDER BY categoria";
        try (PreparedStatement ps = db.conexion().prepareStatement(sql)) {
            ps.setString(1, tipo.name());
            ps.setDate(2, java.sql.Date.valueOf(desde));
            ps.setDate(3, java.sql.Date.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, BigDecimal> totales = new LinkedHashMap<>();
                while (rs.next()) {
                    totales.put(rs.getString(1), rs.getBigDecimal(2));
                }
                return totales;
            }
        } catch (SQLException e) {
            throw new AppException("No se pudieron calcular los totales", e);
        }
    }

    public void borrarTodo() {
        try (Statement st = db.conexion().createStatement()) {
            st.execute("DELETE FROM pendiente");
            st.execute("DELETE FROM movimiento");
        } catch (SQLException e) {
            throw new AppException("No se pudieron borrar los movimientos", e);
        }
    }

    private List<Movimiento> consultar(String sql, List<Object> parametros) {
        try (PreparedStatement ps = db.conexion().prepareStatement(sql)) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Movimiento> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(leer(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new AppException("No se pudieron consultar los movimientos", e);
        }
    }

    private void asignar(PreparedStatement ps, Movimiento m) throws SQLException {
        ps.setString(1, m.tipo().name());
        ps.setDate(2, java.sql.Date.valueOf(m.fecha()));
        ps.setBigDecimal(3, m.monto());
        ps.setString(4, m.concepto());
        ps.setString(5, m.categoria());
        ps.setString(6, hayTexto(m.usuario()) ? m.usuario() : null);
        ps.setString(7, m.formaPago() == null ? null : m.formaPago().name());
        ps.setBoolean(8, m.cancelado());
    }

    private Movimiento leer(ResultSet rs) throws SQLException {
        String forma = rs.getString(8);
        return new Movimiento(rs.getLong(1), TipoMovimiento.valueOf(rs.getString(2)),
                rs.getDate(3).toLocalDate(), rs.getBigDecimal(4), rs.getString(5), rs.getString(6),
                rs.getString(7), forma == null ? null : FormaPago.valueOf(forma), rs.getBoolean(9));
    }

    private static boolean hayTexto(String s) {
        return s != null && !s.isBlank();
    }
}
