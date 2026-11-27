package mx.tecmilenio.svcobranza.db;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.model.Frecuencia;
import mx.tecmilenio.svcobranza.model.Servicio;
import mx.tecmilenio.svcobranza.model.TipoServicio;
import mx.tecmilenio.svcobranza.model.UsuarioAtendido;

public class UsuarioDao {

    private static final String SELECT = "SELECT id, nombre, fecha_ingreso, fecha_registro, esquema, becado, "
            + "frecuencia_estancia, monto_estancia, fecha_base, dia_mes, experiencia, monto_experiencia, "
            + "monto_tiendita FROM usuario_atendido ";

    private final Database db;

    public UsuarioDao(Database db) {
        this.db = db;
    }

    /** Guarda el usuario con sus servicios y devuelve su id. */
    public long insertar(UsuarioAtendido u) {
        String sql = "INSERT INTO usuario_atendido (nombre, fecha_ingreso, fecha_registro, esquema, becado, "
                + "frecuencia_estancia, monto_estancia, fecha_base, dia_mes, experiencia, monto_experiencia, "
                + "monto_tiendita) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.conexion().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            asignar(ps, u);
            ps.executeUpdate();
            long id;
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                id = rs.getLong(1);
            }
            guardarServicios(id, u.servicios());
            return id;
        } catch (SQLException e) {
            throw new AppException("No se pudo guardar el usuario atendido", e);
        }
    }

    public void actualizar(UsuarioAtendido u) {
        String sql = "UPDATE usuario_atendido SET nombre = ?, fecha_ingreso = ?, fecha_registro = ?, "
                + "esquema = ?, becado = ?, frecuencia_estancia = ?, monto_estancia = ?, fecha_base = ?, "
                + "dia_mes = ?, experiencia = ?, monto_experiencia = ?, monto_tiendita = ? WHERE id = ?";
        try (PreparedStatement ps = db.conexion().prepareStatement(sql)) {
            asignar(ps, u);
            ps.setLong(13, u.id());
            ps.executeUpdate();
            guardarServicios(u.id(), u.servicios());
        } catch (SQLException e) {
            throw new AppException("No se pudo actualizar el usuario atendido", e);
        }
    }

    /**
     * Fecha desde la que rige el esquema de cobros vigente del usuario: la de su registro, o la
     * de su ultima edicion. Los cobros anteriores se conservan pero ya no se recalculan.
     */
    public LocalDate cobrosDesde(long usuarioId) {
        try (PreparedStatement ps = db.conexion().prepareStatement(
                "SELECT COALESCE(cobros_desde, fecha_registro) FROM usuario_atendido WHERE id = ?")) {
            ps.setLong(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AppException("El usuario ya no existe.");
                }
                return rs.getDate(1).toLocalDate();
            }
        } catch (SQLException e) {
            throw new AppException("No se pudo leer la fecha de inicio de cobros", e);
        }
    }

    public void fijarCobrosDesde(long usuarioId, LocalDate fecha) {
        try (PreparedStatement ps = db.conexion()
                .prepareStatement("UPDATE usuario_atendido SET cobros_desde = ? WHERE id = ?")) {
            ps.setDate(1, java.sql.Date.valueOf(fecha));
            ps.setLong(2, usuarioId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("No se pudo guardar la fecha de inicio de cobros", e);
        }
    }

    /** Borra al usuario; sus servicios y cobros se borran con el (ON DELETE CASCADE). */
    public void borrar(long id) {
        try (PreparedStatement ps = db.conexion().prepareStatement("DELETE FROM usuario_atendido WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("No se pudo borrar el usuario atendido", e);
        }
    }

    public Optional<UsuarioAtendido> buscarPorId(long id) {
        List<UsuarioAtendido> lista = consultar(SELECT + "WHERE id = ?", List.of(id));
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }

    /** Busca por nombre sin distinguir mayusculas. */
    public Optional<UsuarioAtendido> buscarPorNombre(String nombre) {
        List<UsuarioAtendido> lista = consultar(SELECT + "WHERE LOWER(nombre) = LOWER(?)", List.of(nombre.trim()));
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }

    /** Todos los usuarios, ordenados por nombre. */
    public List<UsuarioAtendido> listar() {
        return consultar(SELECT + "ORDER BY LOWER(nombre)", List.of());
    }

    public List<String> listarNombres() {
        List<String> nombres = new ArrayList<>();
        for (UsuarioAtendido u : listar()) {
            nombres.add(u.nombre());
        }
        return nombres;
    }

    /** Borra todos los usuarios (y, por cascada, sus servicios y cobros). */
    public void borrarTodo() {
        try (Statement st = db.conexion().createStatement()) {
            st.execute("DELETE FROM usuario_atendido");
        } catch (SQLException e) {
            throw new AppException("No se pudieron borrar los usuarios atendidos", e);
        }
    }

    private List<UsuarioAtendido> consultar(String sql, List<Object> parametros) {
        try (PreparedStatement ps = db.conexion().prepareStatement(sql)) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
            List<UsuarioAtendido> lista = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(leer(rs));
                }
            }
            List<UsuarioAtendido> completos = new ArrayList<>();
            for (UsuarioAtendido u : lista) {
                completos.add(conServicios(u));
            }
            return completos;
        } catch (SQLException e) {
            throw new AppException("No se pudieron consultar los usuarios atendidos", e);
        }
    }

    private UsuarioAtendido conServicios(UsuarioAtendido u) throws SQLException {
        String sql = "SELECT tipo, monto, fecha_inicio, recurrencia FROM servicio_usuario "
                + "WHERE usuario_id = ? ORDER BY tipo";
        try (PreparedStatement ps = db.conexion().prepareStatement(sql)) {
            ps.setLong(1, u.id());
            List<Servicio> servicios = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Date inicio = rs.getDate(3);
                    servicios.add(new Servicio(TipoServicio.valueOf(rs.getString(1)), rs.getBigDecimal(2),
                            inicio == null ? null : inicio.toLocalDate(), Frecuencia.valueOf(rs.getString(4))));
                }
            }
            return new UsuarioAtendido(u.id(), u.nombre(), u.fechaIngreso(), u.fechaRegistro(), u.esquema(),
                    u.becado(), u.frecuenciaEstancia(), u.montoEstancia(), u.fechaBase(), u.diaMes(),
                    u.experiencia(), u.montoExperiencia(), u.montoTiendita(), servicios);
        }
    }

    private void guardarServicios(long usuarioId, List<Servicio> servicios) throws SQLException {
        try (PreparedStatement borrar = db.conexion()
                .prepareStatement("DELETE FROM servicio_usuario WHERE usuario_id = ?")) {
            borrar.setLong(1, usuarioId);
            borrar.executeUpdate();
        }
        String sql = "INSERT INTO servicio_usuario (usuario_id, tipo, monto, fecha_inicio, recurrencia) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.conexion().prepareStatement(sql)) {
            for (Servicio s : servicios) {
                ps.setLong(1, usuarioId);
                ps.setString(2, s.tipo().name());
                ps.setBigDecimal(3, s.monto());
                if (s.fechaInicio() == null) {
                    ps.setNull(4, Types.DATE);
                } else {
                    ps.setDate(4, java.sql.Date.valueOf(s.fechaInicio()));
                }
                ps.setString(5, s.recurrencia().name());
                ps.executeUpdate();
            }
        }
    }

    private void asignar(PreparedStatement ps, UsuarioAtendido u) throws SQLException {
        ps.setString(1, u.nombre());
        ps.setDate(2, java.sql.Date.valueOf(u.fechaIngreso()));
        ps.setDate(3, java.sql.Date.valueOf(u.fechaRegistro()));
        ps.setString(4, u.esquema());
        ps.setBoolean(5, u.becado());
        ps.setString(6, u.frecuenciaEstancia() == null ? null : u.frecuenciaEstancia().name());
        ps.setBigDecimal(7, u.montoEstancia());
        if (u.fechaBase() == null) {
            ps.setNull(8, Types.DATE);
        } else {
            ps.setDate(8, java.sql.Date.valueOf(u.fechaBase()));
        }
        if (u.diaMes() == null) {
            ps.setNull(9, Types.INTEGER);
        } else {
            ps.setInt(9, u.diaMes());
        }
        ps.setBoolean(10, u.experiencia());
        ps.setBigDecimal(11, u.montoExperiencia());
        ps.setBigDecimal(12, u.montoTiendita());
    }

    private UsuarioAtendido leer(ResultSet rs) throws SQLException {
        String frecuencia = rs.getString(7);
        java.sql.Date base = rs.getDate(9);
        int dia = rs.getInt(10);
        Integer diaMes = rs.wasNull() ? null : dia;
        BigDecimal estancia = rs.getBigDecimal(8);
        return new UsuarioAtendido(rs.getLong(1), rs.getString(2), rs.getDate(3).toLocalDate(),
                rs.getDate(4).toLocalDate(), rs.getString(5), rs.getBoolean(6),
                frecuencia == null ? null : Frecuencia.valueOf(frecuencia), estancia,
                base == null ? null : base.toLocalDate(), diaMes, rs.getBoolean(11), rs.getBigDecimal(12),
                rs.getBigDecimal(13), List.of());
    }
}
