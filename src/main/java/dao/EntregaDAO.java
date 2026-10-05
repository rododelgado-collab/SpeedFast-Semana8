package dao;

import modelo.Entrega;

import java.util.List;

/**
 * Contrato de persistencia de las entregas, que relacionan un pedido con un repartidor
 * (patrón DAO). El SQL vive en dao.impl.EntregaDAOImpl.
 *
 * @author Rodolfo Delgado
 */
public interface EntregaDAO {

    /** CREATE: inserta la entrega (id_pedido e id_repartidor son claves foráneas) y deja su ID. */
    void create(Entrega entrega);

    /** READ: todas las entregas, la más reciente primero (JOIN con pedido y repartidor). */
    List<Entrega> readAll();

    /** READ de las entregas de un pedido. */
    List<Entrega> readByPedido(int idPedido);

    /** READ de las entregas de un repartidor. */
    List<Entrega> readByRepartidor(int idRepartidor);

    /** READ de una entrega por ID. @return la entrega, o null si no existe */
    Entrega readById(int id);

    /** UPDATE de pedido, repartidor, fecha y hora. @return true si la entrega existía */
    boolean update(Entrega entrega);

    /** DELETE por ID. @return true si se eliminó una fila */
    boolean delete(int idEntrega);

    /** Cuántas entregas tiene un pedido (para avisar antes de eliminarlo). */
    int countByPedido(int idPedido);

    /** Cuántas entregas tiene un repartidor (para avisar antes de eliminarlo). */
    int countByRepartidor(int idRepartidor);
}
