package vista;

import controlador.ControladorEntregas;
import controlador.ControladorPedidos;
import controlador.ControladorRepartidores;
import controlador.EnRuta;
import controlador.Eventos;
import dao.DAOException;
import dao.EntregaDAO;
import dao.PedidoDAO;
import dao.RepartidorDAO;
import dao.impl.EntregaDAOImpl;
import dao.impl.PedidoDAOImpl;
import dao.impl.RepartidorDAOImpl;
import modelo.EstadoPedido;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * VISTA: ventana principal de SpeedFast.
 *
 * Es el punto donde se arma la aplicación (inyección de dependencias a mano):
 *   DAOImpl (JDBC)  ->  controladores (validan y coordinan)  ->  ventanas (Swing).
 * Todos los controladores comparten el mismo objeto Eventos, así cuando una ventana crea,
 * edita o elimina datos las demás ventanas se refrescan solas.
 *
 * Distribución (BorderLayout):
 *   NORTH  -> encabezado
 *   WEST   -> botones de navegación (GridLayout)
 *   CENTER -> bitácora de actividad (JTextArea)
 *   SOUTH  -> barra de resumen
 *
 * @author Rodolfo Delgado
 */
public class VentanaPrincipal extends JFrame {

    private final Eventos eventos = new Eventos();
    private final ControladorPedidos controladorPedidos;
    private final ControladorRepartidores controladorRepartidores;
    private final ControladorEntregas controladorEntregas;

    private final JTextArea txtActividad = new JTextArea();
    private final JLabel lblResumen = new JLabel();

    // Una sola instancia de cada ventana secundaria (no se abren duplicadas)
    private VentanaPedidos ventanaPedidos;
    private VentanaRepartidores ventanaRepartidores;
    private VentanaEntregas ventanaEntregas;
    private VentanaAsignarRepartidor ventanaAsignar;

    public VentanaPrincipal() {
        // Capa de acceso a datos: las interfaces DAO con su implementación JDBC
        PedidoDAO pedidoDAO = new PedidoDAOImpl();
        RepartidorDAO repartidorDAO = new RepartidorDAOImpl();
        EntregaDAO entregaDAO = new EntregaDAOImpl();
        EnRuta enRuta = new EnRuta();

        // Capa de controladores: reciben los DAO (no ejecutan SQL)
        controladorPedidos = new ControladorPedidos(eventos, pedidoDAO, entregaDAO, enRuta);
        controladorRepartidores = new ControladorRepartidores(eventos, repartidorDAO, entregaDAO);
        controladorEntregas = new ControladorEntregas(eventos, entregaDAO, pedidoDAO, enRuta);

        configurarVentana();
        crearComponentes();

        eventos.agregarObservador(this::actualizarResumen);
        eventos.agregarObservadorBitacora(this::agregarActividad);
        agregarActividad("Sistema SpeedFast iniciado.");
        actualizarResumen();

        setVisible(true);
    }

    private void configurarVentana() {
        setTitle("SpeedFast - Gestión de pedidos, repartidores y entregas");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(820, 480);
        setMinimumSize(new Dimension(700, 400));
        setLocationRelativeTo(null);
    }

    private void crearComponentes() {
        JPanel raiz = new JPanel(new BorderLayout(10, 10));
        raiz.setBorder(Estilo.margen(10));

        raiz.add(Estilo.encabezado("SpeedFast",
                "CRUD de pedidos, repartidores y entregas en MySQL (patrón DAO) y simulación de entregas"), BorderLayout.NORTH);

        // --- Menú de botones ---
        JButton btnPedidos = Estilo.boton("Pedidos");
        JButton btnRepartidores = Estilo.boton("Repartidores");
        JButton btnEntregas = Estilo.boton("Entregas");
        JButton btnAsignar = Estilo.boton("Asignar repartidor / Iniciar entrega");
        JButton btnSalir = Estilo.boton("Salir");

        JPanel menu = new JPanel(new GridLayout(5, 1, 0, 10));
        menu.setBorder(BorderFactory.createTitledBorder("Acciones"));
        menu.add(btnPedidos);
        menu.add(btnRepartidores);
        menu.add(btnEntregas);
        menu.add(btnAsignar);
        menu.add(btnSalir);

        JPanel contenedorMenu = new JPanel(new BorderLayout());
        contenedorMenu.add(menu, BorderLayout.NORTH);   // botones arriba, sin estirarse
        raiz.add(contenedorMenu, BorderLayout.WEST);

        // --- Bitácora ---
        txtActividad.setEditable(false);
        txtActividad.setFont(Estilo.MONO);
        txtActividad.setLineWrap(true);
        txtActividad.setWrapStyleWord(true);
        txtActividad.setMargin(new Insets(6, 6, 6, 6));
        JScrollPane scroll = new JScrollPane(txtActividad);
        scroll.setBorder(BorderFactory.createTitledBorder("Actividad"));
        raiz.add(scroll, BorderLayout.CENTER);

        // --- Resumen ---
        lblResumen.setBorder(Estilo.margen(4));
        raiz.add(lblResumen, BorderLayout.SOUTH);

        setContentPane(raiz);

        // --- Eventos (ActionListener con lambdas) ---
        btnPedidos.addActionListener(e -> abrirPedidos());
        btnRepartidores.addActionListener(e -> abrirRepartidores());
        btnEntregas.addActionListener(e -> abrirEntregas());
        btnAsignar.addActionListener(e -> abrirAsignacion());
        btnSalir.addActionListener(e -> salir());
    }

    // ==================================================================
    // Navegación
    // ==================================================================

    private void abrirPedidos() {
        if (ventanaPedidos == null || !ventanaPedidos.isDisplayable()) {
            ventanaPedidos = new VentanaPedidos(controladorPedidos, eventos);
        }
        mostrar(ventanaPedidos);
    }

    private void abrirRepartidores() {
        if (ventanaRepartidores == null || !ventanaRepartidores.isDisplayable()) {
            ventanaRepartidores = new VentanaRepartidores(controladorRepartidores, eventos);
        }
        mostrar(ventanaRepartidores);
    }

    private void abrirEntregas() {
        if (ventanaEntregas == null || !ventanaEntregas.isDisplayable()) {
            ventanaEntregas = new VentanaEntregas(controladorEntregas, controladorPedidos,
                    controladorRepartidores, eventos);
        }
        mostrar(ventanaEntregas);
    }

    private void abrirAsignacion() {
        if (ventanaAsignar == null || !ventanaAsignar.isDisplayable()) {
            ventanaAsignar = new VentanaAsignarRepartidor(controladorPedidos, controladorRepartidores,
                    controladorEntregas, eventos);
        }
        mostrar(ventanaAsignar);
    }

    private void mostrar(JFrame ventana) {
        ventana.setVisible(true);
        ventana.toFront();
    }

    private void salir() {
        int opcion = JOptionPane.showConfirmDialog(this, "¿Desea cerrar SpeedFast?",
                "Salir", JOptionPane.YES_NO_OPTION);
        if (opcion == JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }

    // ==================================================================
    // Actualizaciones que dispara el controlador
    // ==================================================================

    /** Lee los contadores desde MySQL (una consulta con GROUP BY y otra con los repartidores libres). */
    private void actualizarResumen() {
        try {
            Map<EstadoPedido, Integer> cuenta = controladorPedidos.contarPorEstado();
            int total = cuenta.values().stream().mapToInt(Integer::intValue).sum();
            lblResumen.setText("Pedidos: " + total
                    + "   |   Pendientes: " + cuenta.get(EstadoPedido.PENDIENTE)
                    + "   |   En reparto: " + cuenta.get(EstadoPedido.EN_REPARTO)
                    + "   |   Entregados: " + cuenta.get(EstadoPedido.ENTREGADO)
                    + "   |   Repartidores libres: " + controladorRepartidores.disponibles().size());
        } catch (DAOException e) {
            lblResumen.setText("Sin conexión con la base de datos (revise MySQL y db.properties)");
        }
    }

    private void agregarActividad(String linea) {
        txtActividad.append(linea + "\n");
        txtActividad.setCaretPosition(txtActividad.getDocument().getLength());
    }
}
