package controlador;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro (en memoria) de los pedidos que tienen una entrega simulada en curso, es decir,
 * un hilo TareaEntrega ejecutándose. Mientras un pedido está aquí no se puede editar ni
 * eliminar, para que el hilo y el usuario no se pisen. Es seguro entre hilos.
 *
 * @author Rodolfo Delgado
 */
public class EnRuta {

    private final Set<Integer> pedidos = ConcurrentHashMap.newKeySet();

    /** @return true si se marcó; false si ya estaba en ruta */
    public boolean marcar(int idPedido) {
        return pedidos.add(idPedido);
    }

    public void liberar(int idPedido) {
        pedidos.remove(idPedido);
    }

    public boolean contiene(int idPedido) {
        return pedidos.contains(idPedido);
    }
}
