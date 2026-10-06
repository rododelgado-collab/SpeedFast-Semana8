package controlador;

import dao.DAOException;
import dao.EntregaDAO;
import dao.PedidoDAO;
import modelo.Entrega;
import modelo.EstadoPedido;
import modelo.Pedido;
import modelo.Repartidor;
import tareas.TareaEntrega;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * CONTROLADOR (MVC) de las entregas: CRUD de la relación pedido-repartidor y simulación de
 * entregas en hilos. Valida lo que llega desde las ventanas y delega la persistencia en
 * EntregaDAO y PedidoDAO. Nunca ejecuta SQL.
 *
 * @author Rodolfo Delgado
 */
public class ControladorEntregas {

    private final Eventos eventos;
    private final EntregaDAO entregaDAO;
    private final PedidoDAO pedidoDAO;
    private final EnRuta enRuta;

    public ControladorEntregas(Eventos eventos, EntregaDAO entregaDAO, PedidoDAO pedidoDAO, EnRuta enRuta) {
        this.eventos = eventos;
        this.entregaDAO = entregaDAO;
        this.pedidoDAO = pedidoDAO;
        this.enRuta = enRuta;
    }

    // ==================================================================
    // CREATE
    // ==================================================================

    /**
     * Registra una entrega asociando un pedido y un repartidor, con fecha y hora.
     *
     * @param fecha texto con formato AAAA-MM-DD
     * @param hora  texto con formato HH:MM o HH:MM:SS
     * @throws IllegalArgumentException con un mensaje listo para mostrar al usuario
     * @throws DAOException             si falla la base de datos
     */
    public Entrega crear(Pedido pedido, Repartidor repartidor, String fecha, String hora) {
        exigirRelaciones(pedido, repartidor);
        LocalDate dia = leerFecha(fecha);
        exigirFechaEnRango(dia);
        Entrega entrega = new Entrega(0, pedido.getId(), repartidor.getId(), dia, leerHora(hora));
        entregaDAO.create(entrega);
        eventos.registrarEvento("Entrega #" + entrega.getId() + " registrada: pedido #" + pedido.getId()
                + " con " + repartidor.getNombre() + " (" + entrega.getFecha() + " " + entrega.getHora() + ").");
        eventos.notificarCambios();
        return entrega;
    }

    // ==================================================================
    // READ
    // ==================================================================

    public List<Entrega> listar() {
        return entregaDAO.readAll();
    }

    public List<Entrega> listarPorPedido(Pedido pedido) {
        exigirNoNulo(pedido, "Seleccione un pedido para filtrar.");
        return entregaDAO.readByPedido(pedido.getId());
    }

    public List<Entrega> listarPorRepartidor(Repartidor repartidor) {
        exigirNoNulo(repartidor, "Seleccione un repartidor para filtrar.");
        return entregaDAO.readByRepartidor(repartidor.getId());
    }

    // ==================================================================
    // UPDATE
    // ==================================================================

    /**
     * Cambia pedido, repartidor, fecha y hora de una entrega existente.
     *
     * @throws IllegalArgumentException si no hay selección o algún dato no es válido
     * @throws DAOException             si falla la base de datos
     */
    public Entrega actualizar(int id, Pedido pedido, Repartidor repartidor, String fecha, String hora) {
        if (id <= 0) {
            throw new IllegalArgumentException("Seleccione una entrega de la tabla.");
        }
        exigirRelaciones(pedido, repartidor);
        LocalDate dia = leerFecha(fecha);
        Entrega actual = entregaDAO.readById(id);
        // Una entrega antigua se puede seguir editando si no se cambia su fecha
        if (actual == null || !dia.equals(actual.getFecha())) {
            exigirFechaEnRango(dia);
        }
        Entrega entrega = new Entrega(id, pedido.getId(), repartidor.getId(), dia, leerHora(hora));
        if (!entregaDAO.update(entrega)) {
            throw new IllegalArgumentException("La entrega #" + id + " ya no existe en la base de datos.");
        }
        eventos.registrarEvento("Entrega #" + id + " actualizada: pedido #" + pedido.getId()
                + " con " + repartidor.getNombre() + ".");
        eventos.notificarCambios();
        return entrega;
    }

    // ==================================================================
    // DELETE
    // ==================================================================

    /**
     * Elimina una entrega (no se toca el pedido ni el repartidor).
     *
     * @throws IllegalArgumentException si no hay selección o la entrega ya no existe
     * @throws DAOException             si falla la base de datos
     */
    public void eliminar(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Seleccione una entrega de la tabla.");
        }
        if (!entregaDAO.delete(id)) {
            throw new IllegalArgumentException("La entrega #" + id + " ya no existe en la base de datos.");
        }
        eventos.registrarEvento("Entrega #" + id + " eliminada.");
        eventos.notificarCambios();
    }

    // ==================================================================
    // Simulación de entregas con hilos (semanas 4 a 7)
    // ==================================================================

    /**
     * Asigna el repartidor al pedido, deja constancia en MySQL (estado EN_REPARTO y una fila
     * en la tabla entregas con la fecha y hora actuales) y lanza la entrega en un hilo aparte,
     * para que la ventana no se congele mientras se simula el recorrido.
     *
     * @throws IllegalArgumentException si falta algún dato, el pedido no está pendiente o el repartidor está ocupado
     * @throws DAOException             si falla la base de datos
     */
    public void iniciarEntregaSimulada(Pedido pedido, Repartidor repartidor) {
        exigirNoNulo(pedido, "Seleccione un pedido pendiente.");
        exigirNoNulo(repartidor, "Seleccione un repartidor disponible.");
        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new IllegalArgumentException("El pedido #" + pedido.getId() + " ya está " + pedido.getEstado() + ".");
        }
        if (!repartidor.ocupar()) {
            throw new IllegalArgumentException(repartidor.getNombre() + " ya está realizando una entrega.");
        }
        if (!enRuta.marcar(pedido.getId())) {
            repartidor.liberar();
            throw new IllegalArgumentException("El pedido #" + pedido.getId() + " ya tiene una entrega en curso.");
        }

        try {
            pedido.iniciarReparto(repartidor.getNombre());
            pedidoDAO.updateEstado(pedido.getId(), EstadoPedido.EN_REPARTO);
            entregaDAO.create(new Entrega(pedido, repartidor));
        } catch (DAOException | IllegalStateException e) {
            // Si algo falló se deshace todo para no dejar datos a medias
            pedido.volverAPendiente();
            repartidor.liberar();
            enRuta.liberar(pedido.getId());
            try {
                pedidoDAO.updateEstado(pedido.getId(), EstadoPedido.PENDIENTE);
            } catch (DAOException ignorada) {
                // Sin conexión no se puede corregir; el pedido se puede editar a mano después
            }
            throw e;
        }

        eventos.registrarEvento("[" + repartidor.getNombre() + "] Retira pedido #" + pedido.getId()
                + " -> " + pedido.getDireccionEntrega() + " (" + pedido.getTipo()
                + ", " + pedido.calcularTiempoEntrega() + " min estimados)");
        eventos.notificarCambios();

        Thread hilo = new Thread(new TareaEntrega(pedido, repartidor, this),
                "Repartidor-" + repartidor.getNombre());
        hilo.setDaemon(true);   // no impide cerrar la aplicación
        hilo.start();
    }

    /** Lo llama TareaEntrega (desde su hilo) al terminar el recorrido. */
    public void finalizarEntrega(Pedido pedido, Repartidor repartidor) {
        try {
            pedido.marcarEntregado();
            pedidoDAO.updateEstado(pedido.getId(), EstadoPedido.ENTREGADO);
            eventos.registrarEvento("[" + repartidor.getNombre() + "] Pedido #" + pedido.getId()
                    + " ENTREGADO. " + repartidor.getNombre() + " queda disponible.");
        } catch (DAOException e) {
            eventos.registrarEvento("[ERROR] Pedido #" + pedido.getId()
                    + " entregado, pero no se pudo guardar en la BD: " + e.getMessage());
        } finally {
            enRuta.liberar(pedido.getId());
            repartidor.liberar();   // el repartidor siempre queda libre
            eventos.notificarCambios();
        }
    }

    // ==================================================================
    // Validaciones
    // ==================================================================

    private void exigirRelaciones(Pedido pedido, Repartidor repartidor) {
        exigirNoNulo(pedido, "Seleccione un pedido.");
        exigirNoNulo(repartidor, "Seleccione un repartidor.");
    }

    private void exigirNoNulo(Object valor, String mensaje) {
        if (valor == null) {
            throw new IllegalArgumentException(mensaje);
        }
    }

    private LocalDate leerFecha(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalArgumentException("Debe ingresar la fecha (formato AAAA-MM-DD).");
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("La fecha no es válida. Use el formato AAAA-MM-DD, "
                    + "por ejemplo 2026-10-05.");
        }
    }

    /** La fecha de una entrega nueva debe ser de este año y no puede ser futura. */
    private void exigirFechaEnRango(LocalDate dia) {
        LocalDate hoy = LocalDate.now();
        if (dia.getYear() < hoy.getYear()) {
            throw new IllegalArgumentException("La fecha " + dia + " es de un año anterior. "
                    + "Ingrese una fecha de este año (desde " + hoy.getYear() + "-01-01).");
        }
        if (dia.isAfter(hoy)) {
            throw new IllegalArgumentException("La fecha " + dia + " aún no ocurre. "
                    + "La entrega no puede tener una fecha posterior a hoy (" + hoy + ").");
        }
    }

    private LocalTime leerHora(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalArgumentException("Debe ingresar la hora (formato HH:MM o HH:MM:SS).");
        }
        try {
            return LocalTime.parse(texto.trim()).withNano(0);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("La hora no es válida. Use el formato HH:MM o HH:MM:SS, "
                    + "por ejemplo 14:30:00.");
        }
    }
}
