package dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de ConexionBD.describirError. No necesitan base de datos: construyen la
 * SQLException a mano con el código de error que enviaría MySQL.
 *
 * @author Rodolfo Delgado
 */
class ConexionBDTest {

    @Test
    @DisplayName("código 1045: avisa que el usuario o la contraseña son incorrectos")
    void credencialesIncorrectas() {
        String mensaje = ConexionBD.describirError(new SQLException("Access denied", "28000", 1045));

        assertTrue(mensaje.contains("contraseña"));
        assertTrue(mensaje.contains("db.properties"));
    }

    @Test
    @DisplayName("código 1049: avisa que no existe la base de datos")
    void baseInexistente() {
        String mensaje = ConexionBD.describirError(new SQLException("Unknown database", "42000", 1049));

        assertTrue(mensaje.contains("No existe la base"));
    }

    @Test
    @DisplayName("código 1146: avisa que falta una tabla")
    void tablaInexistente() {
        String mensaje = ConexionBD.describirError(new SQLException("Table doesn't exist", "42S02", 1146));

        assertTrue(mensaje.contains("Falta una tabla"));
    }

    @Test
    @DisplayName("código 1451: avisa que otros registros dependen del que se quiere eliminar")
    void claveForaneaAlEliminar() {
        String mensaje = ConexionBD.describirError(new SQLException("Cannot delete or update a parent row", "23000", 1451));

        assertTrue(mensaje.contains("dependen"));
    }

    @Test
    @DisplayName("código 1452: avisa que el pedido o el repartidor no existe")
    void claveForaneaAlInsertar() {
        String mensaje = ConexionBD.describirError(new SQLException("Cannot add or update a child row", "23000", 1452));

        assertTrue(mensaje.contains("no existe"));
    }

    @Test
    @DisplayName("código 1406: avisa que un texto es demasiado largo")
    void textoDemasiadoLargo() {
        String mensaje = ConexionBD.describirError(new SQLException("Data too long", "22001", 1406));

        assertTrue(mensaje.contains("más largo"));
    }

    @Test
    @DisplayName("SQLState 08xxx: avisa que no se pudo conectar con el servidor")
    void servidorApagado() {
        String mensaje = ConexionBD.describirError(new SQLException("Communications link failure", "08S01", 0));

        assertTrue(mensaje.contains("No se pudo conectar con MySQL"));
    }

    @Test
    @DisplayName("cualquier otro código: mensaje genérico con el código original")
    void errorGenerico() {
        String mensaje = ConexionBD.describirError(new SQLException("Algo raro", "HY000", 1234));

        assertTrue(mensaje.contains("Error de base de datos"));
        assertTrue(mensaje.contains("1234"));
    }
}
