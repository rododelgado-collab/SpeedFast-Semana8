package dao.impl;

import dao.ConexionBD;
import dao.DAOException;
import dao.RepartidorDAO;
import modelo.Repartidor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación JDBC de RepartidorDAO sobre la tabla repartidores.
 * Todas las consultas usan PreparedStatement (parámetros con ?) y los recursos
 * (Connection, PreparedStatement, ResultSet) se cierran con try-with-resources.
 * Los errores SQLException se capturan y se relanzan como DAOException con un mensaje claro.
 *
 * @author Rodolfo Delgado
 */
public class RepartidorDAOImpl implements RepartidorDAO {

    @Override
    public void create(Repartidor repartidor) {
        String sql = "INSERT INTO repartidores (nombre) VALUES (?)";
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, repartidor.getNombre());
            ps.executeUpdate();

            try (ResultSet claves = ps.getGeneratedKeys()) {
                if (claves.next()) {
                    repartidor.setId(claves.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DAOException("No se pudo guardar el repartidor. " + ConexionBD.describirError(e), e);
        }
    }

    @Override
    public List<Repartidor> readAll() {
        String sql = "SELECT id, nombre FROM repartidores ORDER BY id";
        List<Repartidor> repartidores = new ArrayList<>();
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                repartidores.add(new Repartidor(rs.getInt("id"), rs.getString("nombre")));
            }
        } catch (SQLException e) {
            throw new DAOException("No se pudieron leer los repartidores. " + ConexionBD.describirError(e), e);
        }
        return repartidores;
    }

    @Override
    public Repartidor readById(int id) {
        String sql = "SELECT id, nombre FROM repartidores WHERE id = ?";
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Repartidor(rs.getInt("id"), rs.getString("nombre"));
                }
            }
        } catch (SQLException e) {
            throw new DAOException("No se pudo leer el repartidor #" + id + ". " + ConexionBD.describirError(e), e);
        }
        return null;
    }

    @Override
    public boolean update(Repartidor repartidor) {
        String sql = "UPDATE repartidores SET nombre = ? WHERE id = ?";
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, repartidor.getNombre());
            ps.setInt(2, repartidor.getId());
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DAOException("No se pudo actualizar el repartidor #" + repartidor.getId() + ". "
                    + ConexionBD.describirError(e), e);
        }
    }

    @Override
    public boolean delete(int idRepartidor) {
        String sql = "DELETE FROM repartidores WHERE id = ?";
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idRepartidor);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DAOException("No se pudo eliminar el repartidor #" + idRepartidor + ". "
                    + ConexionBD.describirError(e), e);
        }
    }
}
