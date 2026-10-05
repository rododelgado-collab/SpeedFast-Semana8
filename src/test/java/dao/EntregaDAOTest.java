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
 * Pruebas CRUD de EntregaDAO (EntregaDAOImpl: INSERT con claves foráneas, SELECT con JOIN
 * y filtros, UPDATE, DELETE y conteos) contra la base speedfast_test.
 *
 * @author Rodolfo Delgado
 */
class EntregaDAOTest extends BaseDaoTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 10, 5);
    private static final LocalTime HORA = LocalTime.of(14, 30, 15);

    // ---------------- CREATE ----------------

    @Test
    @DisplayName("create registra la entrega y le asigna el ID generado por MySQL")
    void createAsignaId() {
        Pedido pedido = guardarPedido("Calle 1", TipoPedido.COMIDA);
        Repartidor repartidor = guardarRepartidor("Juan");
        Entrega entrega = new Entrega(0, pedido.getId(), repartidor.getId(), FECHA, HORA);

        entregaDAO.create(entrega);

        assertTrue(entrega.getId() > 0);
    }

    @Test
    @DisplayName("create con un pedido que no existe viola la clave foránea y lanza DAOException")
    void createPedidoInexistente() {
        Repartidor repartidor = guardarRepartidor("Juan");
        Entrega entrega = new Entrega(0, 9999, repartidor.getId(), FECHA, HORA);

        assertThrows(DAOException.class, () -> entregaDAO.create(entrega));
        assertTrue(entregaDAO.readAll().isEmpty());
    }

    @Test
    @DisplayName("create con un repartidor que no existe viola la clave foránea y lanza DAOException")
    void createRepartidorInexistente() {
        Pedido pedido = guardarPedido("Calle 1", TipoPedido.COMIDA);
        Entrega entrega = new Entrega(0, pedido.getId(), 9999, FECHA, HORA);

        assertThrows(DAOException.class, () -> entregaDAO.create(entrega));
    }

    // ---------------- READ ----------------

    @Test
    @DisplayName("readAll devuelve lista vacía si no hay entregas")
    void readAllSinDatos() {
        assertTrue(entregaDAO.readAll().isEmpty());
    }

    @Test
    @DisplayName("readAll trae fecha, hora, dirección del pedido y nombre del repartidor (JOIN)")
    void readAllTraeDatosDelJoin() {
        Pedido pedido = guardarPedido("Av. Apoquindo 4500", TipoPedido.EXPRESS);
        Repartidor repartidor = guardarRepartidor("María");
        entregaDAO.create(new Entrega(0, pedido.getId(), repartidor.getId(), FECHA, HORA));

        List<Entrega> lista = entregaDAO.readAll();

        assertEquals(1, lista.size());
        Entrega e = lista.get(0);
        assertEquals(pedido.getId(), e.getIdPedido());
        assertEquals(repartidor.getId(), e.getIdRepartidor());
        assertEquals(FECHA, e.getFecha());
        assertEquals(HORA, e.getHora());
        assertEquals("Av. Apoquindo 4500", e.getDireccionPedido());
        assertEquals("María", e.getNombreRepartidor());
    }

    @Test
    @DisplayName("readAll muestra primero la entrega más reciente")
    void readAllOrdenaPorMasReciente() {
        Pedido pedido = guardarPedido("Calle 1", TipoPedido.ENCOMIENDA);
        Repartidor juan = guardarRepartidor("Juan");
        Repartidor maria = guardarRepartidor("María");
        entregaDAO.create(new Entrega(0, pedido.getId(), juan.getId(), FECHA, LocalTime.of(9, 0)));
        entregaDAO.create(new Entrega(0, pedido.getId(), maria.getId(), FECHA, LocalTime.of(10, 0)));

        List<Entrega> lista = entregaDAO.readAll();

        assertEquals(2, lista.size());
        assertEquals("María", lista.get(0).getNombreRepartidor());
        assertEquals("Juan", lista.get(1).getNombreRepartidor());
    }

    @Test
    @DisplayName("readByPedido y readByRepartidor devuelven solo las entregas del pedido o repartidor indicado")
    void readConFiltros() {
        Pedido p1 = guardarPedido("Calle 1", TipoPedido.COMIDA);
        Pedido p2 = guardarPedido("Calle 2", TipoPedido.EXPRESS);
        Repartidor juan = guardarRepartidor("Juan");
        Repartidor maria = guardarRepartidor("María");
        entregaDAO.create(new Entrega(0, p1.getId(), juan.getId(), FECHA, LocalTime.of(9, 0)));
        entregaDAO.create(new Entrega(0, p1.getId(), maria.getId(), FECHA, LocalTime.of(10, 0)));
        entregaDAO.create(new Entrega(0, p2.getId(), juan.getId(), FECHA, LocalTime.of(11, 0)));

        assertEquals(2, entregaDAO.readByPedido(p1.getId()).size());
        assertEquals(1, entregaDAO.readByPedido(p2.getId()).size());
        assertEquals(2, entregaDAO.readByRepartidor(juan.getId()).size());
        assertEquals(1, entregaDAO.readByRepartidor(maria.getId()).size());
        assertTrue(entregaDAO.readByPedido(9999).isEmpty());
        assertTrue(entregaDAO.readByRepartidor(9999).isEmpty());
    }

    @Test
    @DisplayName("readById encuentra la entrega y devuelve null si no existe")
    void readById() {
        Pedido pedido = guardarPedido("Calle 1", TipoPedido.COMIDA);
        Repartidor repartidor = guardarRepartidor("Juan");
        Entrega entrega = new Entrega(0, pedido.getId(), repartidor.getId(), FECHA, HORA);
        entregaDAO.create(entrega);

        Entrega leida = entregaDAO.readById(entrega.getId());

        assertNotNull(leida);
        assertEquals("Juan", leida.getNombreRepartidor());
        assertNull(entregaDAO.readById(9999));
    }

    @Test
    @DisplayName("countByPedido y countByRepartidor cuentan las entregas asociadas")
    void conteos() {
        Pedido p1 = guardarPedido("Calle 1", TipoPedido.COMIDA);
        Pedido p2 = guardarPedido("Calle 2", TipoPedido.EXPRESS);
        Repartidor juan = guardarRepartidor("Juan");
        entregaDAO.create(new Entrega(0, p1.getId(), juan.getId(), FECHA, LocalTime.of(9, 0)));
        entregaDAO.create(new Entrega(0, p1.getId(), juan.getId(), FECHA, LocalTime.of(10, 0)));

        assertEquals(2, entregaDAO.countByPedido(p1.getId()));
        assertEquals(0, entregaDAO.countByPedido(p2.getId()));
        assertEquals(2, entregaDAO.countByRepartidor(juan.getId()));
    }

    // ---------------- UPDATE ----------------

    @Test
    @DisplayName("update cambia pedido, repartidor, fecha y hora de la entrega")
    void updateCambiaTodosLosCampos() {
        Pedido p1 = guardarPedido("Calle 1", TipoPedido.COMIDA);
        Pedido p2 = guardarPedido("Calle 2", TipoPedido.EXPRESS);
        Repartidor juan = guardarRepartidor("Juan");
        Repartidor maria = guardarRepartidor("María");
        Entrega entrega = new Entrega(0, p1.getId(), juan.getId(), FECHA, HORA);
        entregaDAO.create(entrega);

        LocalDate nuevaFecha = LocalDate.of(2026, 11, 20);
        LocalTime nuevaHora = LocalTime.of(8, 5, 0);
        boolean actualizada = entregaDAO.update(new Entrega(entrega.getId(), p2.getId(), maria.getId(), nuevaFecha, nuevaHora));

        assertTrue(actualizada);
        Entrega leida = entregaDAO.readById(entrega.getId());
        assertEquals(p2.getId(), leida.getIdPedido());
        assertEquals(maria.getId(), leida.getIdRepartidor());
        assertEquals(nuevaFecha, leida.getFecha());
        assertEquals(nuevaHora, leida.getHora());
        assertEquals("Calle 2", leida.getDireccionPedido());
    }

    @Test
    @DisplayName("update de una entrega que no existe devuelve false; con un pedido inexistente lanza DAOException")
    void updateCasosLimite() {
        Pedido pedido = guardarPedido("Calle 1", TipoPedido.COMIDA);
        Repartidor repartidor = guardarRepartidor("Juan");
        Entrega entrega = new Entrega(0, pedido.getId(), repartidor.getId(), FECHA, HORA);
        entregaDAO.create(entrega);

        assertFalse(entregaDAO.update(new Entrega(9999, pedido.getId(), repartidor.getId(), FECHA, HORA)));
        assertThrows(DAOException.class,
                () -> entregaDAO.update(new Entrega(entrega.getId(), 9999, repartidor.getId(), FECHA, HORA)));
    }

    // ---------------- DELETE ----------------

    @Test
    @DisplayName("delete elimina solo la entrega indicada y no toca el pedido ni el repartidor")
    void deleteElimina() {
        Pedido pedido = guardarPedido("Calle 1", TipoPedido.COMIDA);
        Repartidor repartidor = guardarRepartidor("Juan");
        Entrega e1 = new Entrega(0, pedido.getId(), repartidor.getId(), FECHA, LocalTime.of(9, 0));
        Entrega e2 = new Entrega(0, pedido.getId(), repartidor.getId(), FECHA, LocalTime.of(10, 0));
        entregaDAO.create(e1);
        entregaDAO.create(e2);

        assertTrue(entregaDAO.delete(e1.getId()));

        List<Entrega> lista = entregaDAO.readAll();
        assertEquals(1, lista.size());
        assertEquals(e2.getId(), lista.get(0).getId());
        assertNotNull(pedidoDAO.readById(pedido.getId()));
        assertNotNull(repartidorDAO.readById(repartidor.getId()));
        assertFalse(entregaDAO.delete(e1.getId()), "Borrarla otra vez devuelve false");
    }
}
