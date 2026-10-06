package controlador;

import dao.DAOException;
import modelo.Entrega;
import modelo.EstadoPedido;
import modelo.Pedido;
import modelo.Repartidor;
import modelo.TipoPedido;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/** Pruebas del controlador de entregas con DAO falsos (no necesitan MySQL). */
class ControladorEntregasTest {

    private DaosFalsos.Pedidos pedidos;
    private DaosFalsos.Repartidores repartidores;
    private DaosFalsos.Entregas entregas;
    private EnRuta enRuta;
    private static final LocalDate HOY = LocalDate.now();
    private ControladorEntregas controlador;
    private Pedido pedido;
    private Repartidor repartidor;

    @BeforeEach
    void preparar() {
        DaosFalsos.caidos = false;
        pedidos = new DaosFalsos.Pedidos();
        repartidores = new DaosFalsos.Repartidores();
        entregas = new DaosFalsos.Entregas();
        enRuta = new EnRuta();
        controlador = new ControladorEntregas(new Eventos(), entregas, pedidos, enRuta);
        pedido = pedidos.agregar("Calle Uno 1", TipoPedido.EXPRESS, EstadoPedido.PENDIENTE);
        repartidor = new Repartidor("Ana Torres");
        repartidores.create(repartidor);
    }

    @AfterEach
    void restaurar() {
        DaosFalsos.caidos = false;
    }

    private String mensajeDe(Runnable accion) {
        return assertThrows(IllegalArgumentException.class, accion::run).getMessage();
    }

    @Test
    @DisplayName("crear registra la entrega con fecha y hora (los segundos sobrantes se descartan)")
    void crear() {
        Entrega e = controlador.crear(pedido, repartidor, " " + HOY + " ", "14:30");
        assertEquals(1, e.getId());
        assertEquals(HOY, e.getFecha());
        assertEquals(LocalTime.of(14, 30), e.getHora());
        assertEquals(pedido.getId(), e.getIdPedido());
        assertEquals(repartidor.getId(), e.getIdRepartidor());
        assertEquals(1, entregas.datos.size());
    }

    @Test
    @DisplayName("crear valida pedido, repartidor, fecha y hora")
    void crearValida() {
        assertTrue(mensajeDe(() -> controlador.crear(null, repartidor, HOY.toString(), "10:00")).contains("pedido"));
        assertTrue(mensajeDe(() -> controlador.crear(pedido, null, HOY.toString(), "10:00")).contains("repartidor"));
        assertTrue(mensajeDe(() -> controlador.crear(pedido, repartidor, "", "10:00")).contains("fecha"));
        assertTrue(mensajeDe(() -> controlador.crear(pedido, repartidor, "05-10-2026", "10:00")).contains("AAAA-MM-DD"));
        assertTrue(mensajeDe(() -> controlador.crear(pedido, repartidor, HOY.getYear() + "-02-30", "10:00")).contains("AAAA-MM-DD"));
        assertTrue(mensajeDe(() -> controlador.crear(pedido, repartidor, HOY.toString(), null)).contains("hora"));
        assertTrue(mensajeDe(() -> controlador.crear(pedido, repartidor, HOY.toString(), "25:00")).contains("HH:MM"));
        assertTrue(mensajeDe(() -> controlador.crear(pedido, repartidor, HOY.toString(), "diez")).contains("HH:MM"));
        assertTrue(entregas.datos.isEmpty());
    }

    @Test
    @DisplayName("crear rechaza fechas de un año anterior y fechas futuras")
    void crearValidaRangoDeFecha() {
        LocalDate anioAnterior = LocalDate.of(HOY.getYear() - 1, 12, 31);
        assertTrue(mensajeDe(() -> controlador.crear(pedido, repartidor, anioAnterior.toString(), "10:00"))
                .contains("año anterior"));
        LocalDate manana = HOY.plusDays(1);
        assertTrue(mensajeDe(() -> controlador.crear(pedido, repartidor, manana.toString(), "10:00"))
                .contains("aún no ocurre"));
        // Los límites del rango sí se aceptan: 1 de enero de este año y hoy
        assertDoesNotThrow(() -> controlador.crear(pedido, repartidor, HOY.getYear() + "-01-01", "10:00"));
        assertDoesNotThrow(() -> controlador.crear(pedido, repartidor, HOY.toString(), "10:00"));
        assertEquals(2, entregas.datos.size(), "las fechas rechazadas no deben guardarse");
    }

    @Test
    @DisplayName("actualizar no deja cambiar la fecha a un año anterior, pero una entrega antigua se puede editar sin tocar su fecha")
    void actualizarValidaRangoDeFecha() {
        Entrega antigua = new Entrega(0, pedido.getId(), repartidor.getId(),
                LocalDate.of(HOY.getYear() - 1, 10, 4), LocalTime.of(11, 30));
        entregas.create(antigua);   // un registro viejo que ya estaba en la base de datos

        String fechaVieja = antigua.getFecha().toString();
        assertDoesNotThrow(() -> controlador.actualizar(antigua.getId(), pedido, repartidor, fechaVieja, "12:00"));

        Entrega nueva = controlador.crear(pedido, repartidor, HOY.toString(), "10:00");
        assertTrue(mensajeDe(() -> controlador.actualizar(nueva.getId(), pedido, repartidor, fechaVieja, "10:00"))
                .contains("año anterior"));
        assertTrue(mensajeDe(() -> controlador.actualizar(nueva.getId(), pedido, repartidor, HOY.plusDays(1).toString(), "10:00"))
                .contains("aún no ocurre"));
        assertEquals(HOY, entregas.datos.get(nueva.getId()).getFecha());
    }

    @Test
    @DisplayName("listar, listarPorPedido y listarPorRepartidor")
    void listar() {
        Pedido otro = pedidos.agregar("Calle Dos 2", TipoPedido.COMIDA, EstadoPedido.PENDIENTE);
        controlador.crear(pedido, repartidor, HOY.toString(), "10:00");
        controlador.crear(otro, repartidor, HOY.toString(), "11:00");

        assertEquals(2, controlador.listar().size());
        assertEquals(1, controlador.listarPorPedido(otro).size());
        assertEquals(2, controlador.listarPorRepartidor(repartidor).size());
        assertTrue(mensajeDe(() -> controlador.listarPorPedido(null)).contains("pedido"));
        assertTrue(mensajeDe(() -> controlador.listarPorRepartidor(null)).contains("repartidor"));
    }

    @Test
    @DisplayName("actualizar cambia los datos de la entrega y valida la selección")
    void actualizar() {
        Entrega e = controlador.crear(pedido, repartidor, HOY.toString(), "10:00");
        controlador.actualizar(e.getId(), pedido, repartidor, HOY.toString(), "08:15:30");
        Entrega guardada = entregas.datos.get(e.getId());
        assertEquals(HOY, guardada.getFecha());
        assertEquals(LocalTime.of(8, 15, 30), guardada.getHora());

        assertTrue(mensajeDe(() -> controlador.actualizar(0, pedido, repartidor, HOY.toString(), "08:15")).contains("Seleccione"));
        assertTrue(mensajeDe(() -> controlador.actualizar(99, pedido, repartidor, HOY.toString(), "08:15")).contains("ya no existe"));
        assertTrue(mensajeDe(() -> controlador.actualizar(e.getId(), pedido, repartidor, "mañana", "08:15")).contains("AAAA-MM-DD"));
    }

    @Test
    @DisplayName("eliminar borra solo la entrega")
    void eliminar() {
        Entrega e = controlador.crear(pedido, repartidor, HOY.toString(), "10:00");
        controlador.eliminar(e.getId());
        assertTrue(entregas.datos.isEmpty());
        assertEquals(1, pedidos.datos.size());
        assertEquals(1, repartidores.datos.size());
        assertTrue(mensajeDe(() -> controlador.eliminar(0)).contains("Seleccione"));
        assertTrue(mensajeDe(() -> controlador.eliminar(e.getId())).contains("ya no existe"));
    }

    @Test
    @DisplayName("iniciarEntregaSimulada deja el pedido EN_REPARTO, crea la entrega y ocupa al repartidor")
    void iniciarEntrega() {
        Pedido guardado = pedidos.datos.get(pedido.getId());
        controlador.iniciarEntregaSimulada(guardado, repartidor);

        assertEquals(EstadoPedido.EN_REPARTO, guardado.getEstado());
        assertFalse(repartidor.isDisponible());
        assertTrue(enRuta.contiene(guardado.getId()));
        assertEquals(1, entregas.datos.size());
    }

    @Test
    @DisplayName("al terminar el hilo el pedido queda ENTREGADO y el repartidor libre")
    void entregaTerminaEnHilo() throws InterruptedException {
        Pedido guardado = pedidos.datos.get(pedido.getId());   // Express: 1,5 s simulados
        controlador.iniciarEntregaSimulada(guardado, repartidor);

        long limite = System.currentTimeMillis() + 5000;
        while (!repartidor.isDisponible() && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }
        assertTrue(repartidor.isDisponible(), "el repartidor debe quedar libre");
        assertFalse(enRuta.contiene(guardado.getId()));
        assertEquals(EstadoPedido.ENTREGADO, guardado.getEstado());
    }

    @Test
    @DisplayName("iniciarEntregaSimulada rechaza datos faltantes, pedidos ya iniciados y repartidores ocupados")
    void iniciarValida() {
        assertTrue(mensajeDe(() -> controlador.iniciarEntregaSimulada(null, repartidor)).contains("pedido"));
        assertTrue(mensajeDe(() -> controlador.iniciarEntregaSimulada(pedido, null)).contains("repartidor"));

        Pedido entregado = pedidos.agregar("Calle Tres 3", TipoPedido.COMIDA, EstadoPedido.ENTREGADO);
        assertTrue(mensajeDe(() -> controlador.iniciarEntregaSimulada(entregado, repartidor)).contains("ya está"));

        repartidor.ocupar();
        assertTrue(mensajeDe(() -> controlador.iniciarEntregaSimulada(pedido, repartidor)).contains("ya está realizando"));
        assertTrue(entregas.datos.isEmpty());
    }

    @Test
    @DisplayName("si falla la base de datos al iniciar, se deshace todo (pedido PENDIENTE, repartidor libre)")
    void iniciarConBdCaida() {
        Pedido guardado = pedidos.datos.get(pedido.getId());
        DaosFalsos.caidos = true;
        assertThrows(DAOException.class, () -> controlador.iniciarEntregaSimulada(guardado, repartidor));
        DaosFalsos.caidos = false;

        assertEquals(EstadoPedido.PENDIENTE, guardado.getEstado());
        assertTrue(repartidor.isDisponible());
        assertFalse(enRuta.contiene(guardado.getId()));
        assertTrue(entregas.datos.isEmpty());
    }
}
