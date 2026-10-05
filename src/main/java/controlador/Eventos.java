package controlador;

import javax.swing.SwingUtilities;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Aviso de cambios entre controladores y ventanas (patrón Observador).
 *
 * Las ventanas se suscriben y, cuando cualquier controlador crea, edita o elimina algo,
 * todas refrescan sus tablas y combos. Los tres controladores comparten la MISMA instancia.
 * Swing no es thread-safe, así que se avisa siempre dentro del Event Dispatch Thread
 * (las entregas simuladas terminan en otro hilo).
 *
 * @author Rodolfo Delgado
 */
public class Eventos {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    // CopyOnWrite: se pueden agregar/quitar observadores mientras se notifica
    private final List<Runnable> observadoresCambios = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> observadoresBitacora = new CopyOnWriteArrayList<>();

    public void agregarObservador(Runnable observador) {
        observadoresCambios.add(observador);
    }

    public void quitarObservador(Runnable observador) {
        observadoresCambios.remove(observador);
    }

    public void agregarObservadorBitacora(Consumer<String> observador) {
        observadoresBitacora.add(observador);
    }

    /** Avisa a las ventanas que los datos cambiaron para que se recarguen desde la base de datos. */
    public void notificarCambios() {
        SwingUtilities.invokeLater(() -> {
            for (Runnable o : observadoresCambios) {
                o.run();
            }
        });
    }

    /** Agrega una línea (con la hora) a la bitácora de la ventana principal. */
    public void registrarEvento(String mensaje) {
        String linea = LocalTime.now().format(HORA) + "  " + mensaje;
        SwingUtilities.invokeLater(() -> {
            for (Consumer<String> o : observadoresBitacora) {
                o.accept(linea);
            }
        });
    }
}
