package dao;

import modelo.EstadoPedido;
import modelo.Pedido;
import modelo.TipoPedido;

import java.util.List;
import java.util.Map;

/**
 * Contrato de persistencia de los pedidos (patrón DAO). La interfaz solo declara las
 * operaciones CRUD; el SQL vive en dao.impl.PedidoDAOImpl.
 * Los controladores dependen de esta interfaz, no de la implementación JDBC.
 *
 * @author Rodolfo Delgado
 */
public interface PedidoDAO {

    /** CREATE: inserta el pedido y deja en él el ID que generó MySQL. */
    void create(Pedido pedido);

    /** READ: todos los pedidos, cada uno con el repartidor de su última entrega (si tiene). */
    List<Pedido> readAll();

    /** READ con filtros opcionales: un filtro null significa "sin filtrar por ese dato". */
    List<Pedido> readByFiltros(EstadoPedido estado, TipoPedido tipo);

    /** READ de un pedido por su ID. @return el pedido, o null si no existe */
    Pedido readById(int id);

    /** UPDATE de dirección, tipo y estado. @return true si el pedido existía */
    boolean update(Pedido pedido);

    /** UPDATE solo del estado (lo usa la simulación de entregas). @return true si el pedido existía */
    boolean updateEstado(int idPedido, EstadoPedido nuevoEstado);

    /** DELETE por ID. @return true si se eliminó una fila */
    boolean delete(int idPedido);

    /** Cuántos pedidos hay en cada estado (consulta con GROUP BY). */
    Map<EstadoPedido, Integer> contarPorEstado();
}
