package mx.tecmilenio.svcobranza.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import mx.tecmilenio.svcobranza.AppException;

/** Pares clave/valor de la configuracion de la aplicacion (dias de aviso, ultimo aviso...). */
public class ConfiguracionDao {

    private final Database db;

    public ConfiguracionDao(Database db) {
        this.db = db;
    }

    public Optional<String> obtener(String clave) {
        try (PreparedStatement ps = db.conexion().prepareStatement("SELECT valor FROM configuracion WHERE clave = ?")) {
            ps.setString(1, clave);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AppException("No se pudo leer la configuración", e);
        }
    }

    public void guardar(String clave, String valor) {
        try (PreparedStatement ps = db.conexion()
                .prepareStatement("MERGE INTO configuracion (clave, valor) KEY (clave) VALUES (?, ?)")) {
            ps.setString(1, clave);
            ps.setString(2, valor);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("No se pudo guardar la configuración", e);
        }
    }

    public Map<String, String> todas() {
        try (Statement st = db.conexion().createStatement();
                ResultSet rs = st.executeQuery("SELECT clave, valor FROM configuracion ORDER BY clave")) {
            Map<String, String> mapa = new LinkedHashMap<>();
            while (rs.next()) {
                mapa.put(rs.getString(1), rs.getString(2));
            }
            return mapa;
        } catch (SQLException e) {
            throw new AppException("No se pudo leer la configuración", e);
        }
    }

    public void borrarTodo() {
        try (Statement st = db.conexion().createStatement()) {
            st.execute("DELETE FROM configuracion");
        } catch (SQLException e) {
            throw new AppException("No se pudo borrar la configuración", e);
        }
    }
}
