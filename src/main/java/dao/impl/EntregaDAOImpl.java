package dao.impl;

import dao.ConexionBD;
import dao.DAOException;
import dao.EntregaDAO;
import modelo.Entrega;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación JDBC de EntregaDAO sobre la tabla entregas, que relaciona pedidos y
 * repartidores mediante claves foráneas. Usa PreparedStatement y try-with-resources.
 *
 * @author Rodolfo Delgado
 */
public class EntregaDAOImpl implements EntregaDAO {

    /** SELECT común: JOIN con pedidos y repartidores para mostrar dirección y nombre. */
    private static final String SELECT_BASE =
            "SELECT e.id, e.id_pedido, e.id_repartidor, e.fecha, e.hora, p.direccion, r.nombre "
                    + "FROM entregas e "
                    + "JOIN pedidos p ON p.id = e.id_pedido "
                    + "JOIN repartidores r ON r.id = e.id_repartidor ";

    @Override
    public void create(Entrega entrega) {
        String sql = "INSERT INTO entregas (id_pedido, id_repartidor, fecha, hora) VALUES (?, ?, ?, ?)";
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, entrega.getIdPedido());
            ps.setInt(2, entrega.getIdRepartidor());
            ps.setDate(3, Date.valueOf(entrega.getFecha()));
            ps.setTime(4, Time.valueOf(entrega.getHora()));
            ps.executeUpdate();

            try (ResultSet claves = ps.getGeneratedKeys()) {
                if (claves.next()) {
                    entrega.setId(claves.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DAOException("No se pudo registrar la entrega. " + ConexionBD.describirError(e), e);
        }
    }

    @Override
    public List<Entrega> readAll() {
        return consultar("", null, "No se pudo leer el historial de entregas. ");
    }

    @Override
    public List<Entrega> readByPedido(int idPedido) {
        return consultar("WHERE e.id_pedido = ? ", idPedido,
                "No se pudieron leer las entregas del pedido #" + idPedido + ". ");
    }

    @Override
    public List<Entrega> readByRepartidor(int idRepartidor) {
        return consultar("WHERE e.id_repartidor = ? ", idRepartidor,
                "No se pudieron leer las entregas del repartidor #" + idRepartidor + ". ");
    }

    @Override
    public Entrega readById(int id) {
        List<Entrega> encontradas = consultar("WHERE e.id = ? ", id,
                "No se pudo leer la entrega #" + id + ". ");
        return encontradas.isEmpty() ? null : encontradas.get(0);
    }

    @Override
    public boolean update(Entrega entrega) {
        String sql = "UPDATE entregas SET id_pedido = ?, id_repartidor = ?, fecha = ?, hora = ? WHERE id = ?";
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, entrega.getIdPedido());
            ps.setInt(2, entrega.getIdRepartidor());
            ps.setDate(3, Date.valueOf(entrega.getFecha()));
            ps.setTime(4, Time.valueOf(entrega.getHora()));
            ps.setInt(5, entrega.getId());
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DAOException("No se pudo actualizar la entrega #" + entrega.getId() + ". "
                    + ConexionBD.describirError(e), e);
        }
    }

    @Override
    public boolean delete(int idEntrega) {
        String sql = "DELETE FROM entregas WHERE id = ?";
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idEntrega);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DAOException("No se pudo eliminar la entrega #" + idEntrega + ". "
                    + ConexionBD.describirError(e), e);
        }
    }

    @Override
    public int countByPedido(int idPedido) {
        return contar("SELECT COUNT(*) FROM entregas WHERE id_pedido = ?", idPedido);
    }

    @Override
    public int countByRepartidor(int idRepartidor) {
        return contar("SELECT COUNT(*) FROM entregas WHERE id_repartidor = ?", idRepartidor);
    }

    // ------------------------------------------------------------------
    // Métodos privados reutilizados por los públicos
    // ------------------------------------------------------------------

    /** Ejecuta el SELECT_BASE con un filtro opcional (con un parámetro ?) y arma la lista. */
    private List<Entrega> consultar(String filtro, Integer parametro, String mensajeError) {
        String sql = SELECT_BASE + filtro + "ORDER BY e.id DESC";
        List<Entrega> entregas = new ArrayList<>();
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (parametro != null) {
                ps.setInt(1, parametro);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Entrega e = new Entrega(
                            rs.getInt("id"),
                            rs.getInt("id_pedido"),
                            rs.getInt("id_repartidor"),
                            rs.getDate("fecha").toLocalDate(),
                            rs.getTime("hora").toLocalTime());
                    e.setDatosVisibles(rs.getString("direccion"), rs.getString("nombre"));
                    entregas.add(e);
                }
            }
        } catch (SQLException e) {
            throw new DAOException(mensajeError + ConexionBD.describirError(e), e);
        }
        return entregas;
    }

    private int contar(String sql, int id) {
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DAOException("No se pudieron contar las entregas. " + ConexionBD.describirError(e), e);
        }
    }
}
