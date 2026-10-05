package dao;

import modelo.Repartidor;

import java.util.List;

/**
 * Contrato de persistencia de los repartidores (patrón DAO).
 * El SQL vive en dao.impl.RepartidorDAOImpl.
 *
 * @author Rodolfo Delgado
 */
public interface RepartidorDAO {

    /** CREATE: inserta el repartidor y deja en él el ID generado por MySQL. */
    void create(Repartidor repartidor);

    /** READ: todos los repartidores ordenados por ID. */
    List<Repartidor> readAll();

    /** READ de un repartidor por ID. @return el repartidor, o null si no existe */
    Repartidor readById(int id);

    /** UPDATE del nombre. @return true si el repartidor existía */
    boolean update(Repartidor repartidor);

    /** DELETE por ID. @return true si se eliminó una fila */
    boolean delete(int idRepartidor);
}
