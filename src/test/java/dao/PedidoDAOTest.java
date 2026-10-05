package dao;

import modelo.Entrega;
import modelo.EstadoPedido;
import modelo.Pedido;
import modelo.Repartidor;
import modelo.TipoPedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas CRUD de PedidoDAO (PedidoDAOImpl: INSERT, SELECT con LEFT JOIN y filtros, UPDATE,
 * DELETE y GROUP BY) contra la base speedfast_test.
 *
 * @author Rodolfo Delgado
 */
class PedidoDAOTest extends BaseDaoTest {

    // ---------------- CREATE ----------------

    @Test
    @DisplayName("create deja en el pedido el ID generado por MySQL")
    void createAsignaId() {
        Pedido p = Pedido.crear(0, "Av. Providencia 123", TipoPedido.COMIDA);

        pedidoDAO.create(p);

        assertTrue(p.getId() > 0);
    }

    @Test
    @DisplayName("una dirección con SQL malicioso se guarda como texto (PreparedStatement evita la inyección)")
    void direccionConSqlMalicioso() {
        String maliciosa = "x'); DROP TABLE pedidos; --";

        guardarPedido(maliciosa, TipoPedido.COMIDA);

        List<Pedido> lista = pedidoDAO.readAll();   // si la tabla se hubiera borrado, esto fallaría
        assertEquals(1, lista.size());
        assertEquals(maliciosa, lista.get(0).getDireccionEntrega());
    }

    // ---------------- READ ----------------

    @Test
    @DisplayName("readAll devuelve lista vacía si no hay pedidos")
    void readAllSinDatos() {
        assertTrue(pedidoDAO.readAll().isEmpty());
    }

    @Test
    @DisplayName("readAll reconstruye la subclase correcta, la dirección y el estado de cada pedido")
    void createYReadAllConservaDatos() {
        guardarPedido("Calle 1", TipoPedido.COMIDA);
        guardarPedido("Calle 2", TipoPedido.ENCOMIENDA);
        guardarPedido("Calle 3", TipoPedido.EXPRESS);

        List<Pedido> lista = pedidoDAO.readAll();

        assertEquals(3, lista.size());
        assertEquals("Calle 1", lista.get(0).getDireccionEntrega());
        assertEquals(TipoPedido.COMIDA, lista.get(0).getTipo());
        assertEquals(TipoPedido.ENCOMIENDA, lista.get(1).getTipo());
        assertEquals(TipoPedido.EXPRESS, lista.get(2).getTipo());
        for (Pedido p : lista) {
            assertEquals(EstadoPedido.PENDIENTE, p.getEstado());
            assertNull(p.getRepartidorAsignado(), "Sin entregas no hay repartidor");
        }
    }

    @Test
    @DisplayName("readByFiltros filtra por estado, por tipo, por ambos y sin filtros")
    void readByFiltros() {
        Pedido comidaPendiente = guardarPedido("Calle 1", TipoPedido.COMIDA);
        Pedido comidaEntregada = guardarPedido("Calle 2", TipoPedido.COMIDA);
        Pedido expressEntregado = guardarPedido("Calle 3", TipoPedido.EXPRESS);
        pedidoDAO.updateEstado(comidaEntregada.getId(), EstadoPedido.ENTREGADO);
        pedidoDAO.updateEstado(expressEntregado.getId(), EstadoPedido.ENTREGADO);

        assertEquals(3, pedidoDAO.readByFiltros(null, null).size());
        assertEquals(2, pedidoDAO.readByFiltros(EstadoPedido.ENTREGADO, null).size());
        assertEquals(2, pedidoDAO.readByFiltros(null, TipoPedido.COMIDA).size());

        List<Pedido> ambos = pedidoDAO.readByFiltros(EstadoPedido.PENDIENTE, TipoPedido.COMIDA);
        assertEquals(1, ambos.size());
        assertEquals(comidaPendiente.getId(), ambos.get(0).getId());

        assertTrue(pedidoDAO.readByFiltros(EstadoPedido.EN_REPARTO, null).isEmpty());
    }

    @Test
    @DisplayName("readById encuentra el pedido y devuelve null si no existe")
    void readById() {
        Pedido p = guardarPedido("Calle 1", TipoPedido.EXPRESS);

        Pedido leido = pedidoDAO.readById(p.getId());

        assertNotNull(leido);
        assertEquals("Calle 1", leido.getDireccionEntrega());
        assertEquals(TipoPedido.EXPRESS, leido.getTipo());
        assertNull(pedidoDAO.readById(9999));
    }

    @Test
    @DisplayName("readAll trae el repartidor de la ÚLTIMA entrega del pedido")
    void readAllTraeRepartidorDeUltimaEntrega() {
        Pedido pedido = guardarPedido("Calle X", TipoPedido.ENCOMIENDA);
        Repartidor juan = guardarRepartidor("Juan");
        Repartidor maria = guardarRepartidor("María");
        // Primer intento con Juan (interrumpido) y segundo intento con María
        entregaDAO.create(new Entrega(0, pedido.getId(), juan.getId(), LocalDate.of(2026, 10, 1), LocalTime.of(9, 0)));
        entregaDAO.create(new Entrega(0, pedido.getId(), maria.getId(), LocalDate.of(2026, 10, 1), LocalTime.of(10, 0)));

        Pedido leido = pedidoDAO.readAll().get(0);

        assertEquals("María", leido.getRepartidorAsignado());
    }

    @Test
    @DisplayName("contarPorEstado cuenta cada estado y pone 0 en los que no tienen pedidos")
    void contarPorEstado() {
        Pedido p1 = guardarPedido("Calle 1", TipoPedido.COMIDA);
        guardarPedido("Calle 2", TipoPedido.COMIDA);
        Pedido p3 = guardarPedido("Calle 3", TipoPedido.EXPRESS);
        pedidoDAO.updateEstado(p1.getId(), EstadoPedido.ENTREGADO);
        pedidoDAO.updateEstado(p3.getId(), EstadoPedido.ENTREGADO);

        Map<EstadoPedido, Integer> cuenta = pedidoDAO.contarPorEstado();

        assertEquals(1, cuenta.get(EstadoPedido.PENDIENTE));
        assertEquals(0, cuenta.get(EstadoPedido.EN_REPARTO));
        assertEquals(2, cuenta.get(EstadoPedido.ENTREGADO));
    }

    // ---------------- UPDATE ----------------

    @Test
    @DisplayName("update cambia dirección, tipo y estado del pedido indicado y de ningún otro")
    void updateCambiaTodosLosCampos() {
        Pedido a = guardarPedido("Calle A", TipoPedido.COMIDA);
        Pedido b = guardarPedido("Calle B", TipoPedido.EXPRESS);

        Pedido editado = Pedido.crear(a.getId(), "Calle A Nueva 99", TipoPedido.ENCOMIENDA);
        editado.restaurarDesdeBD(EstadoPedido.EN_REPARTO, null);

        assertTrue(pedidoDAO.update(editado));

        Pedido leidoA = pedidoDAO.readById(a.getId());
        assertEquals("Calle A Nueva 99", leidoA.getDireccionEntrega());
        assertEquals(TipoPedido.ENCOMIENDA, leidoA.getTipo());
        assertEquals(EstadoPedido.EN_REPARTO, leidoA.getEstado());
        Pedido leidoB = pedidoDAO.readById(b.getId());
        assertEquals("Calle B", leidoB.getDireccionEntrega());
        assertEquals(EstadoPedido.PENDIENTE, leidoB.getEstado());
    }

    @Test
    @DisplayName("update de un pedido que no existe devuelve false")
    void updateInexistente() {
        Pedido fantasma = Pedido.crear(9999, "Calle Fantasma", TipoPedido.COMIDA);
        assertFalse(pedidoDAO.update(fantasma));
    }

    @Test
    @DisplayName("updateEstado cambia solo el estado")
    void updateEstado() {
        Pedido a = guardarPedido("Calle A", TipoPedido.COMIDA);
        Pedido b = guardarPedido("Calle B", TipoPedido.EXPRESS);

        assertTrue(pedidoDAO.updateEstado(a.getId(), EstadoPedido.ENTREGADO));

        assertEquals(EstadoPedido.ENTREGADO, pedidoDAO.readById(a.getId()).getEstado());
        assertEquals("Calle A", pedidoDAO.readById(a.getId()).getDireccionEntrega());
        assertEquals(EstadoPedido.PENDIENTE, pedidoDAO.readById(b.getId()).getEstado());
        assertFalse(pedidoDAO.updateEstado(9999, EstadoPedido.ENTREGADO));
    }

    // ---------------- DELETE ----------------

    @Test
    @DisplayName("delete elimina solo el pedido indicado y devuelve true")
    void deleteElimina() {
        Pedido a = guardarPedido("Calle A", TipoPedido.COMIDA);
        Pedido b = guardarPedido("Calle B", TipoPedido.EXPRESS);

        assertTrue(pedidoDAO.delete(a.getId()));

        List<Pedido> lista = pedidoDAO.readAll();
        assertEquals(1, lista.size());
        assertEquals(b.getId(), lista.get(0).getId());
        assertFalse(pedidoDAO.delete(a.getId()), "Borrarlo otra vez devuelve false");
    }

    @Test
    @DisplayName("delete de un pedido con entregas viola la clave foránea y lanza DAOException")
    void deleteConEntregasViolaClaveForanea() {
        Pedido pedido = guardarPedido("Calle 1", TipoPedido.COMIDA);
        Repartidor juan = guardarRepartidor("Juan");
        entregaDAO.create(new Entrega(0, pedido.getId(), juan.getId(), LocalDate.of(2026, 10, 5), LocalTime.of(9, 0)));

        DAOException ex = assertThrows(DAOException.class, () -> pedidoDAO.delete(pedido.getId()));

        assertTrue(ex.getMessage().contains("dependen"), "El mensaje debe explicar el motivo: " + ex.getMessage());
        assertNotNull(pedidoDAO.readById(pedido.getId()), "El pedido debe seguir existiendo");
    }
}
