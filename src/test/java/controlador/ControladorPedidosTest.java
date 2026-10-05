package controlador;

import dao.DAOException;
import modelo.Entrega;
import modelo.EstadoPedido;
import modelo.Pedido;
import modelo.PedidoExpress;
import modelo.TipoPedido;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Pruebas del controlador de pedidos con DAO falsos (no necesitan MySQL). */
class ControladorPedidosTest {

    private DaosFalsos.Pedidos pedidos;
    private DaosFalsos.Entregas entregas;
    private EnRuta enRuta;
    private ControladorPedidos controlador;

    @BeforeEach
    void preparar() {
        DaosFalsos.caidos = false;
        pedidos = new DaosFalsos.Pedidos();
        entregas = new DaosFalsos.Entregas();
        enRuta = new EnRuta();
        controlador = new ControladorPedidos(new Eventos(), pedidos, entregas, enRuta);
    }

    @AfterEach
    void restaurar() {
        DaosFalsos.caidos = false;
    }

    private String mensajeDe(Runnable accion) {
        return assertThrows(IllegalArgumentException.class, accion::run).getMessage();
    }

    @Test
    @DisplayName("crear guarda un pedido de la subclase correcta, con ID y estado elegidos")
    void crear() {
        Pedido p = controlador.crear("  Av. Providencia   1234 ", TipoPedido.EXPRESS, EstadoPedido.PENDIENTE);
        assertEquals(1, p.getId());
        assertInstanceOf(PedidoExpress.class, p);
        assertEquals("Av. Providencia 1234", p.getDireccionEntrega());
        assertEquals(EstadoPedido.PENDIENTE, p.getEstado());
        assertEquals(1, pedidos.datos.size());
    }

    @Test
    @DisplayName("crear valida dirección, tipo y estado antes de llegar a la base de datos")
    void crearValida() {
        assertTrue(mensajeDe(() -> controlador.crear(null, TipoPedido.COMIDA, EstadoPedido.PENDIENTE)).contains("dirección"));
        assertTrue(mensajeDe(() -> controlador.crear("   ", TipoPedido.COMIDA, EstadoPedido.PENDIENTE)).contains("dirección"));
        assertTrue(mensajeDe(() -> controlador.crear("abc", TipoPedido.COMIDA, EstadoPedido.PENDIENTE)).contains("al menos 5"));
        assertTrue(mensajeDe(() -> controlador.crear("x".repeat(101), TipoPedido.COMIDA, EstadoPedido.PENDIENTE)).contains("100"));
        assertTrue(mensajeDe(() -> controlador.crear("Calle Falsa 123", null, EstadoPedido.PENDIENTE)).contains("tipo"));
        assertTrue(mensajeDe(() -> controlador.crear("Calle Falsa 123", TipoPedido.COMIDA, null)).contains("estado"));
        assertTrue(pedidos.datos.isEmpty());
    }

    @Test
    @DisplayName("listar con filtros y pendientes devuelven solo lo que corresponde")
    void listarFiltros() {
        pedidos.agregar("Calle Uno 1", TipoPedido.COMIDA, EstadoPedido.PENDIENTE);
        pedidos.agregar("Calle Dos 2", TipoPedido.EXPRESS, EstadoPedido.ENTREGADO);
        pedidos.agregar("Calle Tres 3", TipoPedido.EXPRESS, EstadoPedido.PENDIENTE);

        assertEquals(3, controlador.listar().size());
        assertEquals(2, controlador.listar(null, TipoPedido.EXPRESS).size());
        assertEquals(1, controlador.listar(EstadoPedido.ENTREGADO, TipoPedido.EXPRESS).size());
        assertEquals(2, controlador.pendientes().size());
        Map<EstadoPedido, Integer> conteo = controlador.contarPorEstado();
        assertEquals(2, conteo.get(EstadoPedido.PENDIENTE));
        assertEquals(0, conteo.get(EstadoPedido.EN_REPARTO));
    }

    @Test
    @DisplayName("actualizar cambia dirección, tipo y estado")
    void actualizar() {
        Pedido p = pedidos.agregar("Calle Uno 1", TipoPedido.COMIDA, EstadoPedido.PENDIENTE);
        Pedido nuevo = controlador.actualizar(p.getId(), "Calle Nueva 99", TipoPedido.ENCOMIENDA, EstadoPedido.ENTREGADO);
        assertEquals(TipoPedido.ENCOMIENDA, nuevo.getTipo());
        Pedido guardado = pedidos.datos.get(p.getId());
        assertEquals("Calle Nueva 99", guardado.getDireccionEntrega());
        assertEquals(EstadoPedido.ENTREGADO, guardado.getEstado());
    }

    @Test
    @DisplayName("actualizar valida selección, existencia y que no haya una entrega en curso")
    void actualizarValida() {
        Pedido p = pedidos.agregar("Calle Uno 1", TipoPedido.COMIDA, EstadoPedido.PENDIENTE);
        assertTrue(mensajeDe(() -> controlador.actualizar(0, "Calle Nueva 99", TipoPedido.COMIDA, EstadoPedido.PENDIENTE)).contains("Seleccione"));
        assertTrue(mensajeDe(() -> controlador.actualizar(99, "Calle Nueva 99", TipoPedido.COMIDA, EstadoPedido.PENDIENTE)).contains("ya no existe"));
        assertTrue(mensajeDe(() -> controlador.actualizar(p.getId(), "abc", TipoPedido.COMIDA, EstadoPedido.PENDIENTE)).contains("al menos 5"));

        enRuta.marcar(p.getId());
        assertTrue(mensajeDe(() -> controlador.actualizar(p.getId(), "Calle Nueva 99", TipoPedido.COMIDA, EstadoPedido.PENDIENTE)).contains("entrega en curso"));
        assertEquals("Calle Uno 1", pedidos.datos.get(p.getId()).getDireccionEntrega());
    }

    @Test
    @DisplayName("eliminar borra el pedido")
    void eliminar() {
        Pedido p = pedidos.agregar("Calle Uno 1", TipoPedido.COMIDA, EstadoPedido.PENDIENTE);
        controlador.eliminar(p.getId());
        assertTrue(pedidos.datos.isEmpty());
    }

    @Test
    @DisplayName("eliminar valida selección, existencia, entrega en curso y entregas registradas")
    void eliminarValida() {
        Pedido p = pedidos.agregar("Calle Uno 1", TipoPedido.COMIDA, EstadoPedido.PENDIENTE);
        assertTrue(mensajeDe(() -> controlador.eliminar(-1)).contains("Seleccione"));
        assertTrue(mensajeDe(() -> controlador.eliminar(99)).contains("ya no existe"));

        entregas.create(new Entrega(0, p.getId(), 1, LocalDate.now(), LocalTime.now()));
        assertTrue(mensajeDe(() -> controlador.eliminar(p.getId())).contains("1 entrega"));

        entregas.datos.clear();
        enRuta.marcar(p.getId());
        assertTrue(mensajeDe(() -> controlador.eliminar(p.getId())).contains("entrega en curso"));
        assertEquals(1, pedidos.datos.size());
    }

    @Test
    @DisplayName("un error de la base de datos se propaga como DAOException")
    void bdCaida() {
        DaosFalsos.caidos = true;
        assertThrows(DAOException.class, () -> controlador.crear("Calle Uno 1", TipoPedido.COMIDA, EstadoPedido.PENDIENTE));
        assertThrows(DAOException.class, () -> controlador.listar());
        assertEquals(List.of(), pedidos.datos.values().stream().toList());
    }
}
