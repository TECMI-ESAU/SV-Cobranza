package mx.tecmilenio.svcobranza.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import mx.tecmilenio.svcobranza.AppException;

/** Calendario global de fechas de experiencia. */
public class ExperienciaDao {

    private final Database db;

    public ExperienciaDao(Database db) {
        this.db = db;
    }

    /** Todas las fechas, de la mas antigua a la mas reciente. */
    public List<LocalDate> listar() {
        try (Statement st = db.conexion().createStatement();
                ResultSet rs = st.executeQuery("SELECT fecha FROM experiencia_fecha ORDER BY fecha")) {
            List<LocalDate> fechas = new ArrayList<>();
            while (rs.next()) {
                fechas.add(rs.getDate(1).toLocalDate());
            }
            return fechas;
        } catch (SQLException e) {
            throw new AppException("No se pudo leer el calendario de experiencia", e);
        }
    }

    public void insertar(LocalDate fecha) {
        try (PreparedStatement ps = db.conexion().prepareStatement("INSERT INTO experiencia_fecha (fecha) VALUES (?)")) {
            ps.setDate(1, java.sql.Date.valueOf(fecha));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("No se pudo guardar la fecha de experiencia", e);
        }
    }

    /** Borra las fechas iguales o posteriores a la dada. */
    public void borrarDesde(LocalDate fecha) {
        try (PreparedStatement ps = db.conexion().prepareStatement("DELETE FROM experiencia_fecha WHERE fecha >= ?")) {
            ps.setDate(1, java.sql.Date.valueOf(fecha));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("No se pudo actualizar el calendario de experiencia", e);
        }
    }

    public void borrarTodo() {
        try (Statement st = db.conexion().createStatement()) {
            st.execute("DELETE FROM experiencia_fecha");
        } catch (SQLException e) {
            throw new AppException("No se pudo borrar el calendario de experiencia", e);
        }
    }
}
