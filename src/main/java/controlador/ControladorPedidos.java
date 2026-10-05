package controlador;

import dao.EntregaDAO;
import dao.PedidoDAO;
import modelo.EstadoPedido;
import modelo.Pedido;
import modelo.TipoPedido;

import java.util.List;
import java.util.Map;

/**
 * CONTROLADOR (MVC) de los pedidos: valida lo que llega desde las ventanas y delega la
 * persistencia en PedidoDAO. Nunca ejecuta SQL.
 *
 * @author Rodolfo Delgado
 */
public class ControladorPedidos {

    private static final int DIRECCION_MIN = 5;
    private static final int DIRECCION_MAX = 100;   // VARCHAR(100) en la tabla pedidos

    private final Eventos eventos;
    private final PedidoDAO pedidoDAO;
    private final EntregaDAO entregaDAO;
    private final EnRuta enRuta;

    public ControladorPedidos(Eventos eventos, PedidoDAO pedidoDAO, EntregaDAO entregaDAO, EnRuta enRuta) {
        this.eventos = eventos;
        this.pedidoDAO = pedidoDAO;
        this.entregaDAO = entregaDAO;
        this.enRuta = enRuta;
    }

    // ==================================================================
    // CREATE
    // ==================================================================

    /**
     * Valida los datos del formulario, crea el pedido del tipo elegido y lo guarda en MySQL.
     * El ID lo genera la base de datos (AUTO_INCREMENT).
     *
     * @throws IllegalArgumentException con un mensaje listo para mostrar al usuario
     * @throws dao.DAOException         si falla la base de datos
     */
    public Pedido crear(String direccion, TipoPedido tipo, EstadoPedido estado) {
        String limpia = validar(direccion, tipo, estado);

        Pedido pedido = Pedido.crear(0, limpia, tipo);   // Polimorfismo: crea la subclase según el tipo
        pedido.restaurarDesdeBD(estado, null);
        pedidoDAO.create(pedido);
        eventos.registrarEvento("Pedido #" + pedido.getId() + " guardado (" + pedido.getTipo() + ", "
                + pedido.getEstado() + ").");
        eventos.notificarCambios();
        return pedido;
    }

    // ==================================================================
    // READ
    // ==================================================================

    /** Todos los pedidos guardados en MySQL, con su repartidor. */
    public List<Pedido> listar() {
        return pedidoDAO.readAll();
    }

    /** Filtros opcionales: null significa "todos" para ese dato. */
    public List<Pedido> listar(EstadoPedido estado, TipoPedido tipo) {
        return pedidoDAO.readByFiltros(estado, tipo);
    }

    /** Pedidos que se pueden asignar a un repartidor. */
    public List<Pedido> pendientes() {
        return pedidoDAO.readByFiltros(EstadoPedido.PENDIENTE, null);
    }

    public Map<EstadoPedido, Integer> contarPorEstado() {
        return pedidoDAO.contarPorEstado();
    }

    // ==================================================================
    // UPDATE
    // ==================================================================

    /**
     * Cambia dirección, tipo y estado de un pedido existente.
     *
     * @throws IllegalArgumentException si no hay selección, los datos no son válidos o hay una entrega en curso
     * @throws dao.DAOException         si falla la base de datos
     */
    public Pedido actualizar(int id, String direccion, TipoPedido tipo, EstadoPedido estado) {
        exigirSeleccion(id);
        exigirNoEnRuta(id, "editarlo");
        String limpia = validar(direccion, tipo, estado);

        Pedido pedido = Pedido.crear(id, limpia, tipo);
        pedido.restaurarDesdeBD(estado, null);
        if (!pedidoDAO.update(pedido)) {
            throw new IllegalArgumentException("El pedido #" + id + " ya no existe en la base de datos.");
        }
        eventos.registrarEvento("Pedido #" + id + " actualizado (" + tipo + ", " + estado + ").");
        eventos.notificarCambios();
        return pedido;
    }

    // ==================================================================
    // DELETE
    // ==================================================================

    /**
     * Elimina un pedido. No se permite si tiene una entrega en curso o entregas registradas
     * (la clave foránea lo impediría): primero hay que eliminar esas entregas.
     *
     * @throws IllegalArgumentException con el motivo, listo para mostrar al usuario
     * @throws dao.DAOException         si falla la base de datos
     */
    public void eliminar(int id) {
        exigirSeleccion(id);
        exigirNoEnRuta(id, "eliminarlo");
        int entregas = entregaDAO.countByPedido(id);
        if (entregas > 0) {
            throw new IllegalArgumentException("El pedido #" + id + " tiene " + entregas
                    + " entrega(s) registrada(s). Elimine esas entregas antes de borrar el pedido.");
        }
        if (!pedidoDAO.delete(id)) {
            throw new IllegalArgumentException("El pedido #" + id + " ya no existe en la base de datos.");
        }
        eventos.registrarEvento("Pedido #" + id + " eliminado.");
        eventos.notificarCambios();
    }

    // ==================================================================
    // Validaciones
    // ==================================================================

    /** Devuelve la dirección sin espacios sobrantes o lanza IllegalArgumentException con el motivo. */
    private String validar(String direccion, TipoPedido tipo, EstadoPedido estado) {
        if (direccion == null || direccion.trim().isEmpty()) {
            throw new IllegalArgumentException("Debe ingresar la dirección de entrega.");
        }
        String limpia = direccion.trim().replaceAll("\\s+", " ");
        if (limpia.length() < DIRECCION_MIN) {
            throw new IllegalArgumentException("La dirección debe tener al menos " + DIRECCION_MIN + " caracteres.");
        }
        if (limpia.length() > DIRECCION_MAX) {
            throw new IllegalArgumentException("La dirección no puede superar los " + DIRECCION_MAX + " caracteres.");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("Debe seleccionar un tipo de pedido.");
        }
        if (estado == null) {
            throw new IllegalArgumentException("Debe seleccionar un estado.");
        }
        return limpia;
    }

    private void exigirSeleccion(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Seleccione un pedido de la tabla.");
        }
    }

    private void exigirNoEnRuta(int id, String accion) {
        if (enRuta.contiene(id)) {
            throw new IllegalArgumentException("El pedido #" + id + " tiene una entrega en curso. "
                    + "Espere a que termine para " + accion + ".");
        }
    }
}
