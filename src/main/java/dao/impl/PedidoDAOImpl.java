package dao.impl;

import dao.ConexionBD;
import dao.DAOException;
import dao.PedidoDAO;
import modelo.EstadoPedido;
import modelo.Pedido;
import modelo.TipoPedido;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Implementación JDBC de PedidoDAO sobre la tabla pedidos. Es la única clase que conoce
 * el SQL de los pedidos. Todas las consultas usan PreparedStatement y try-with-resources.
 *
 * @author Rodolfo Delgado
 */
public class PedidoDAOImpl implements PedidoDAO {

    /**
     * SELECT común: el LEFT JOIN trae el repartidor de la última entrega de cada pedido
     * (null si todavía no tiene ninguna).
     */
    private static final String SELECT_BASE =
            "SELECT p.id, p.direccion, p.tipo, p.estado, r.nombre AS repartidor "
                    + "FROM pedidos p "
                    + "LEFT JOIN entregas e ON e.id = (SELECT MAX(e2.id) FROM entregas e2 WHERE e2.id_pedido = p.id) "
                    + "LEFT JOIN repartidores r ON r.id = e.id_repartidor ";

    @Override
    public void create(Pedido pedido) {
        String sql = "INSERT INTO pedidos (direccion, tipo, estado) VALUES (?, ?, ?)";
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, pedido.getDireccionEntrega());
            ps.setString(2, pedido.getTipo().name());
            ps.setString(3, pedido.getEstado().name());
            ps.executeUpdate();

            try (ResultSet claves = ps.getGeneratedKeys()) {
                if (claves.next()) {
                    pedido.setId(claves.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DAOException("No se pudo guardar el pedido. " + ConexionBD.describirError(e), e);
        }
    }

    @Override
    public List<Pedido> readAll() {
        return readByFiltros(null, null);
    }

    @Override
    public List<Pedido> readByFiltros(EstadoPedido estado, TipoPedido tipo) {
        // "? IS NULL OR ..." deja el filtro sin efecto cuando el parámetro es null
        String sql = SELECT_BASE
                + "WHERE (? IS NULL OR p.estado = ?) AND (? IS NULL OR p.tipo = ?) "
                + "ORDER BY p.id";
        String filtroEstado = estado == null ? null : estado.name();
        String filtroTipo = tipo == null ? null : tipo.name();

        List<Pedido> pedidos = new ArrayList<>();
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, filtroEstado);
            ps.setString(2, filtroEstado);
            ps.setString(3, filtroTipo);
            ps.setString(4, filtroTipo);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pedidos.add(mapear(rs));
                }
            }
        } catch (SQLException e) {
            throw new DAOException("No se pudieron leer los pedidos. " + ConexionBD.describirError(e), e);
        }
        return pedidos;
    }

    @Override
    public Pedido readById(int id) {
        String sql = SELECT_BASE + "WHERE p.id = ?";
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapear(rs);
                }
            }
        } catch (SQLException e) {
            throw new DAOException("No se pudo leer el pedido #" + id + ". " + ConexionBD.describirError(e), e);
        }
        return null;
    }

    @Override
    public boolean update(Pedido pedido) {
        String sql = "UPDATE pedidos SET direccion = ?, tipo = ?, estado = ? WHERE id = ?";
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, pedido.getDireccionEntrega());
            ps.setString(2, pedido.getTipo().name());
            ps.setString(3, pedido.getEstado().name());
            ps.setInt(4, pedido.getId());
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DAOException("No se pudo actualizar el pedido #" + pedido.getId() + ". "
                    + ConexionBD.describirError(e), e);
        }
    }

    @Override
    public boolean updateEstado(int idPedido, EstadoPedido nuevoEstado) {
        String sql = "UPDATE pedidos SET estado = ? WHERE id = ?";
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nuevoEstado.name());
            ps.setInt(2, idPedido);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DAOException("No se pudo actualizar el estado del pedido #" + idPedido + ". "
                    + ConexionBD.describirError(e), e);
        }
    }

    @Override
    public boolean delete(int idPedido) {
        String sql = "DELETE FROM pedidos WHERE id = ?";
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idPedido);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DAOException("No se pudo eliminar el pedido #" + idPedido + ". "
                    + ConexionBD.describirError(e), e);
        }
    }

    @Override
    public Map<EstadoPedido, Integer> contarPorEstado() {
        String sql = "SELECT estado, COUNT(*) AS total FROM pedidos GROUP BY estado";
        Map<EstadoPedido, Integer> cuenta = new EnumMap<>(EstadoPedido.class);
        for (EstadoPedido e : EstadoPedido.values()) {
            cuenta.put(e, 0);
        }
        try (Connection conn = ConexionBD.conectar();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                cuenta.put(EstadoPedido.valueOf(rs.getString("estado")), rs.getInt("total"));
            }
        } catch (SQLException e) {
            throw new DAOException("No se pudo contar los pedidos. " + ConexionBD.describirError(e), e);
        }
        return cuenta;
    }

    /** Convierte la fila actual del ResultSet en un Pedido de la subclase que corresponde al tipo. */
    private Pedido mapear(ResultSet rs) throws SQLException {
        Pedido p = Pedido.crear(
                rs.getInt("id"),
                rs.getString("direccion"),
                TipoPedido.valueOf(rs.getString("tipo")));
        p.restaurarDesdeBD(EstadoPedido.valueOf(rs.getString("estado")), rs.getString("repartidor"));
        return p;
    }
}
