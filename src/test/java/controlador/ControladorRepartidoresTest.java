package controlador;

import dao.DAOException;
import modelo.Entrega;
import modelo.Repartidor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pruebas del controlador de repartidores con DAO falsos (no necesitan MySQL). */
class ControladorRepartidoresTest {

    private DaosFalsos.Repartidores repartidores;
    private DaosFalsos.Entregas entregas;
    private ControladorRepartidores controlador;

    @BeforeEach
    void preparar() {
        DaosFalsos.caidos = false;
        repartidores = new DaosFalsos.Repartidores();
        entregas = new DaosFalsos.Entregas();
        controlador = new ControladorRepartidores(new Eventos(), repartidores, entregas);
    }

    @AfterEach
    void restaurar() {
        DaosFalsos.caidos = false;
    }

    private String mensajeDe(Runnable accion) {
        return assertThrows(IllegalArgumentException.class, accion::run).getMessage();
    }

    @Test
    @DisplayName("crear guarda el repartidor, limpia espacios sobrantes y le deja su ID")
    void crearGuarda() {
        Repartidor r = controlador.crear("   Juan    Pérez  ");
        assertEquals(1, r.getId());
        assertEquals("Juan Pérez", r.getNombre());
        assertEquals(1, repartidores.datos.size());
    }

    @Test
    @DisplayName("crear rechaza nombres vacíos, muy cortos, muy largos o con caracteres inválidos")
    void crearValida() {
        assertTrue(mensajeDe(() -> controlador.crear(null)).contains("Debe ingresar"));
        assertTrue(mensajeDe(() -> controlador.crear("    ")).contains("Debe ingresar"));
        assertTrue(mensajeDe(() -> controlador.crear("A")).contains("al menos 2"));
        assertTrue(mensajeDe(() -> controlador.crear("A".repeat(101))).contains("100"));
        assertTrue(mensajeDe(() -> controlador.crear("Juan123")).contains("letras"));
        assertTrue(mensajeDe(() -> controlador.crear("1Juan")).contains("letras"));
        assertTrue(mensajeDe(() -> controlador.crear("Robert'); DROP TABLE repartidores;--")).contains("letras"));
        assertTrue(repartidores.datos.isEmpty(), "ningún nombre inválido debe llegar a la base de datos");
    }

    @Test
    @DisplayName("crear acepta tildes, eñes, apóstrofes, puntos y guiones")
    void crearAceptaNombresReales() {
        assertDoesNotThrow(() -> controlador.crear("María José Muñoz-O'Higgins Jr."));
    }

    @Test
    @DisplayName("crear rechaza un nombre repetido sin importar mayúsculas")
    void crearRechazaDuplicado() {
        controlador.crear("Ana Torres");
        assertTrue(mensajeDe(() -> controlador.crear("ANA torres")).contains("Ya existe"));
        assertEquals(1, repartidores.datos.size());
    }

    @Test
    @DisplayName("listar devuelve los repartidores guardados y conserva el estado disponible del objeto")
    void listarConservaDisponibilidad() {
        Repartidor creado = controlador.crear("Luis Soto");
        assertTrue(creado.ocupar());
        List<Repartidor> lista = controlador.listar();
        assertEquals(1, lista.size());
        assertSame(creado, lista.get(0), "debe reutilizar el objeto para no perder 'en ruta'");
        assertFalse(lista.get(0).isDisponible());
        assertTrue(controlador.disponibles().isEmpty());
    }

    @Test
    @DisplayName("actualizar cambia el nombre y valida la selección y el nombre nuevo")
    void actualizar() {
        Repartidor r = controlador.crear("Pedro Rojo");
        controlador.crear("Ana Torres");

        Repartidor editado = controlador.actualizar(r.getId(), "Pedro Rojas");
        assertEquals("Pedro Rojas", editado.getNombre());
        assertEquals("Pedro Rojas", repartidores.datos.get(r.getId()).getNombre());

        assertTrue(mensajeDe(() -> controlador.actualizar(0, "Nombre Válido")).contains("Seleccione"));
        assertTrue(mensajeDe(() -> controlador.actualizar(r.getId(), "ana torres")).contains("Ya existe"));
        assertTrue(mensajeDe(() -> controlador.actualizar(r.getId(), "X")).contains("al menos 2"));
        assertTrue(mensajeDe(() -> controlador.actualizar(99, "Nombre Válido")).contains("ya no existe"));
    }

    @Test
    @DisplayName("actualizar permite dejar el mismo nombre (no cuenta como duplicado)")
    void actualizarMismoNombre() {
        Repartidor r = controlador.crear("Pedro Rojo");
        assertDoesNotThrow(() -> controlador.actualizar(r.getId(), "pedro rojo"));
    }

    @Test
    @DisplayName("actualizar no deja editar a un repartidor que está en una entrega")
    void actualizarOcupado() {
        Repartidor r = controlador.crear("Pedro Rojo");
        r.ocupar();
        assertTrue(mensajeDe(() -> controlador.actualizar(r.getId(), "Pedro Rojas")).contains("realizando una entrega"));
        assertEquals("Pedro Rojo", repartidores.datos.get(r.getId()).getNombre());
    }

    @Test
    @DisplayName("si la base de datos falla al actualizar, el nombre en memoria vuelve al anterior")
    void actualizarConBdCaida() {
        Repartidor r = controlador.crear("Pedro Rojo");
        controlador.listar();
        DaosFalsos.caidos = true;
        // readAll del duplicado falla primero: se lanza DAOException y nada cambia
        assertThrows(DAOException.class, () -> controlador.actualizar(r.getId(), "Pedro Rojas"));
        DaosFalsos.caidos = false;
        assertEquals("Pedro Rojo", controlador.listar().get(0).getNombre());
    }

    @Test
    @DisplayName("eliminar borra el repartidor")
    void eliminar() {
        Repartidor r = controlador.crear("Pedro Rojo");
        controlador.eliminar(r.getId());
        assertTrue(repartidores.datos.isEmpty());
        assertTrue(controlador.listar().isEmpty());
    }

    @Test
    @DisplayName("eliminar valida selección, existencia, entrega en curso y entregas registradas")
    void eliminarValida() {
        Repartidor r = controlador.crear("Pedro Rojo");
        assertTrue(mensajeDe(() -> controlador.eliminar(0)).contains("Seleccione"));
        assertTrue(mensajeDe(() -> controlador.eliminar(99)).contains("ya no existe"));

        entregas.create(new Entrega(0, 1, r.getId(), LocalDate.now(), LocalTime.now()));
        assertTrue(mensajeDe(() -> controlador.eliminar(r.getId())).contains("1 entrega"));
        assertEquals(1, repartidores.datos.size());

        entregas.datos.clear();
        r.ocupar();
        assertTrue(mensajeDe(() -> controlador.eliminar(r.getId())).contains("realizando una entrega"));
        assertEquals(1, repartidores.datos.size());
    }

    @Test
    @DisplayName("un error de la base de datos se propaga como DAOException (la ventana lo muestra)")
    void bdCaida() {
        DaosFalsos.caidos = true;
        assertThrows(DAOException.class, () -> controlador.crear("Pedro Rojo"));
        assertThrows(DAOException.class, () -> controlador.listar());
    }
}
