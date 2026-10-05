package dao;

import modelo.Entrega;
import modelo.Pedido;
import modelo.Repartidor;
import modelo.TipoPedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas CRUD de RepartidorDAO (RepartidorDAOImpl) contra la base speedfast_test.
 *
 * @author Rodolfo Delgado
 */
class RepartidorDAOTest extends BaseDaoTest {

    // ---------------- CREATE ----------------

    @Test
    @DisplayName("create deja en el repartidor el ID generado por MySQL")
    void createAsignaId() {
        Repartidor r = new Repartidor("Juan");
        assertEquals(0, r.getId(), "Antes de guardar el ID vale 0");

        repartidorDAO.create(r);

        assertTrue(r.getId() > 0, "MySQL debe generar un ID (AUTO_INCREMENT)");
    }

    @Test
    @DisplayName("create con un nombre más largo que la columna (100) lanza DAOException y no guarda nada")
    void createNombreDemasiadoLargo() {
        Repartidor r = new Repartidor("A".repeat(101));

        assertThrows(DAOException.class, () -> repartidorDAO.create(r));
        assertTrue(repartidorDAO.readAll().isEmpty());
    }

    // ---------------- READ ----------------

    @Test
    @DisplayName("readAll devuelve lista vacía si no hay repartidores")
    void readAllSinDatos() {
        assertTrue(repartidorDAO.readAll().isEmpty());
    }

    @Test
    @DisplayName("readAll devuelve lo guardado, ordenado por ID")
    void readAllDevuelveLoGuardado() {
        Repartidor juan = guardarRepartidor("Juan");
        Repartidor maria = guardarRepartidor("María");

        List<Repartidor> lista = repartidorDAO.readAll();

        assertEquals(2, lista.size());
        assertEquals(juan.getId(), lista.get(0).getId());
        assertEquals("Juan", lista.get(0).getNombre());
        assertEquals(maria.getId(), lista.get(1).getId());
        assertEquals("María", lista.get(1).getNombre());
    }

    @Test
    @DisplayName("los nombres con tildes y eñes se guardan sin deformarse")
    void nombresConCaracteresEspeciales() {
        guardarRepartidor("Ñandú Pérez");

        assertEquals("Ñandú Pérez", repartidorDAO.readAll().get(0).getNombre());
    }

    @Test
    @DisplayName("readById encuentra el repartidor y devuelve null si no existe")
    void readById() {
        Repartidor juan = guardarRepartidor("Juan");

        Repartidor leido = repartidorDAO.readById(juan.getId());

        assertNotNull(leido);
        assertEquals("Juan", leido.getNombre());
        assertNull(repartidorDAO.readById(9999));
    }

    // ---------------- UPDATE ----------------

    @Test
    @DisplayName("update cambia el nombre y devuelve true")
    void updateCambiaNombre() {
        Repartidor r = guardarRepartidor("Juan");
        Repartidor otro = guardarRepartidor("María");

        boolean actualizado = repartidorDAO.update(new Repartidor(r.getId(), "Juan Pablo"));

        assertTrue(actualizado);
        assertEquals("Juan Pablo", repartidorDAO.readById(r.getId()).getNombre());
        assertEquals("María", repartidorDAO.readById(otro.getId()).getNombre(), "No debe cambiar a los demás");
    }

    @Test
    @DisplayName("update de un repartidor que no existe devuelve false")
    void updateInexistente() {
        assertFalse(repartidorDAO.update(new Repartidor(9999, "Nadie")));
    }

    // ---------------- DELETE ----------------

    @Test
    @DisplayName("delete elimina solo el repartidor indicado y devuelve true")
    void deleteElimina() {
        Repartidor juan = guardarRepartidor("Juan");
        Repartidor maria = guardarRepartidor("María");

        assertTrue(repartidorDAO.delete(juan.getId()));

        List<Repartidor> lista = repartidorDAO.readAll();
        assertEquals(1, lista.size());
        assertEquals(maria.getId(), lista.get(0).getId());
    }

    @Test
    @DisplayName("delete de un repartidor que no existe devuelve false")
    void deleteInexistente() {
        assertFalse(repartidorDAO.delete(9999));
    }

    @Test
    @DisplayName("delete de un repartidor con entregas viola la clave foránea y lanza DAOException")
    void deleteConEntregasViolaClaveForanea() {
        Pedido pedido = guardarPedido("Calle 1", TipoPedido.COMIDA);
        Repartidor juan = guardarRepartidor("Juan");
        entregaDAO.create(new Entrega(0, pedido.getId(), juan.getId(), LocalDate.of(2026, 10, 5), LocalTime.of(9, 0)));

        DAOException ex = assertThrows(DAOException.class, () -> repartidorDAO.delete(juan.getId()));

        assertTrue(ex.getMessage().contains("dependen"), "El mensaje debe explicar el motivo: " + ex.getMessage());
        assertNotNull(repartidorDAO.readById(juan.getId()), "El repartidor debe seguir existiendo");
    }
}
