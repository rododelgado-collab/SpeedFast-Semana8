package controlador;

import dao.EntregaDAO;
import dao.RepartidorDAO;
import modelo.Repartidor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * CONTROLADOR (MVC) de los repartidores: valida los datos que vienen de la ventana y
 * delega la persistencia en RepartidorDAO. Nunca ejecuta SQL.
 *
 * Los repartidores se guardan en MySQL, pero "disponible / en ruta" es un estado momentáneo
 * de la simulación con hilos: por eso se conservan en memoria los objetos ya leídos
 * (cache por id) y cada lectura de la base de datos reutiliza esos objetos.
 *
 * @author Rodolfo Delgado
 */
public class ControladorRepartidores {

    /** Letras (con tildes y eñes), espacios, puntos, apóstrofes y guiones. Debe partir con una letra. */
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("^\\p{L}[\\p{L} .'-]*$");

    private final Eventos eventos;
    private final RepartidorDAO repartidorDAO;
    private final EntregaDAO entregaDAO;
    private final Map<Integer, Repartidor> cache = new ConcurrentHashMap<>();

    public ControladorRepartidores(Eventos eventos, RepartidorDAO repartidorDAO, EntregaDAO entregaDAO) {
        this.eventos = eventos;
        this.repartidorDAO = repartidorDAO;
        this.entregaDAO = entregaDAO;
    }

    // ==================================================================
    // CREATE
    // ==================================================================

    /**
     * Valida el nombre y guarda un repartidor nuevo.
     *
     * @throws IllegalArgumentException con un mensaje listo para mostrar al usuario
     * @throws dao.DAOException         si falla la base de datos
     */
    public Repartidor crear(String nombre) {
        String limpio = validarNombre(nombre, 0);

        Repartidor repartidor = new Repartidor(limpio);
        repartidorDAO.create(repartidor);
        cache.put(repartidor.getId(), repartidor);
        eventos.registrarEvento("Repartidor " + repartidor.getNombre() + " (id " + repartidor.getId()
                + ") guardado en la base de datos.");
        eventos.notificarCambios();
        return repartidor;
    }

    // ==================================================================
    // READ
    // ==================================================================

    /** Todos los repartidores desde MySQL (reutiliza los objetos para no perder "disponible / en ruta"). */
    public List<Repartidor> listar() {
        List<Repartidor> desdeBD = repartidorDAO.readAll();
        List<Repartidor> resultado = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        for (Repartidor leido : desdeBD) {
            Repartidor conocido = cache.computeIfAbsent(leido.getId(), id -> leido);
            conocido.setNombre(leido.getNombre());
            resultado.add(conocido);
            ids.add(leido.getId());
        }
        cache.keySet().retainAll(ids);   // los que ya no existen en la BD salen de la memoria
        return resultado;
    }

    /** Solo los repartidores que no están haciendo una entrega ahora mismo. */
    public List<Repartidor> disponibles() {
        List<Repartidor> libres = new ArrayList<>();
        for (Repartidor r : listar()) {
            if (r.isDisponible()) {
                libres.add(r);
            }
        }
        return libres;
    }

    // ==================================================================
    // UPDATE
    // ==================================================================

    /**
     * Cambia el nombre de un repartidor existente.
     *
     * @throws IllegalArgumentException si no hay selección, el nombre no es válido o está en una entrega
     * @throws dao.DAOException         si falla la base de datos
     */
    public Repartidor actualizar(int id, String nombre) {
        exigirSeleccion(id);
        String limpio = validarNombre(nombre, id);

        Repartidor existente = buscar(id);
        if (!existente.isDisponible()) {
            throw new IllegalArgumentException(existente.getNombre()
                    + " está realizando una entrega. Espere a que termine para editarlo.");
        }

        String anterior = existente.getNombre();
        existente.setNombre(limpio);
        try {
            if (!repartidorDAO.update(existente)) {
                throw new IllegalArgumentException("El repartidor #" + id + " ya no existe en la base de datos.");
            }
        } catch (RuntimeException e) {
            existente.setNombre(anterior);   // si falló, la memoria queda igual que la base de datos
            throw e;
        }
        eventos.registrarEvento("Repartidor #" + id + " actualizado: " + anterior + " -> " + limpio + ".");
        eventos.notificarCambios();
        return existente;
    }

    // ==================================================================
    // DELETE
    // ==================================================================

    /**
     * Elimina un repartidor. No se permite si está en una entrega o si ya tiene entregas
     * registradas (la clave foránea lo impediría).
     *
     * @throws IllegalArgumentException con el motivo, listo para mostrar al usuario
     * @throws dao.DAOException         si falla la base de datos
     */
    public void eliminar(int id) {
        exigirSeleccion(id);
        Repartidor existente = buscar(id);
        if (!existente.isDisponible()) {
            throw new IllegalArgumentException(existente.getNombre()
                    + " está realizando una entrega. Espere a que termine para eliminarlo.");
        }
        int entregas = entregaDAO.countByRepartidor(id);
        if (entregas > 0) {
            throw new IllegalArgumentException(existente.getNombre() + " tiene " + entregas
                    + " entrega(s) registrada(s). Elimine o reasigne esas entregas antes de borrarlo.");
        }
        if (!repartidorDAO.delete(id)) {
            throw new IllegalArgumentException("El repartidor #" + id + " ya no existe en la base de datos.");
        }
        cache.remove(id);
        eventos.registrarEvento("Repartidor " + existente.getNombre() + " (id " + id + ") eliminado.");
        eventos.notificarCambios();
    }

    // ==================================================================
    // Validaciones y ayudas
    // ==================================================================

    /** Devuelve el nombre sin espacios sobrantes o lanza IllegalArgumentException con el motivo. */
    private String validarNombre(String nombre, int idPropio) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("Debe ingresar el nombre del repartidor.");
        }
        String limpio = nombre.trim().replaceAll("\\s+", " ");
        if (limpio.length() < 2) {
            throw new IllegalArgumentException("El nombre debe tener al menos 2 caracteres.");
        }
        if (limpio.length() > 100) {
            throw new IllegalArgumentException("El nombre no puede superar los 100 caracteres.");
        }
        if (!NOMBRE_VALIDO.matcher(limpio).matches()) {
            throw new IllegalArgumentException("El nombre solo puede tener letras, espacios, puntos, "
                    + "apóstrofes y guiones, y debe comenzar con una letra.");
        }
        for (Repartidor r : repartidorDAO.readAll()) {
            if (r.getId() != idPropio && r.getNombre().equalsIgnoreCase(limpio)) {
                throw new IllegalArgumentException("Ya existe un repartidor llamado " + r.getNombre() + ".");
            }
        }
        return limpio;
    }

    private void exigirSeleccion(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Seleccione un repartidor de la tabla.");
        }
    }

    /** Busca un repartidor existente (reutiliza el objeto en memoria). */
    private Repartidor buscar(int id) {
        for (Repartidor r : listar()) {
            if (r.getId() == id) {
                return r;
            }
        }
        throw new IllegalArgumentException("El repartidor #" + id + " ya no existe en la base de datos.");
    }
}
