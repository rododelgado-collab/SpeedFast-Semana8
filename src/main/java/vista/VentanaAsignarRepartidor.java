package vista;

import controlador.ControladorEntregas;
import controlador.ControladorPedidos;
import controlador.ControladorRepartidores;
import controlador.Eventos;
import dao.DAOException;
import modelo.Pedido;
import modelo.Repartidor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * VISTA: asignar un repartidor disponible a un pedido pendiente e iniciar la
 * entrega. Los pedidos pendientes se leen de MySQL y al iniciar se registra la
 * entrega en la tabla entregas. La entrega se simula en un hilo (tareas.TareaEntrega), así la
 * interfaz sigue respondiendo mientras el repartidor "va en camino".
 *
 * @author Rodolfo Delgado
 */
public class VentanaAsignarRepartidor extends JFrame {

    private final ControladorPedidos controladorPedidos;
    private final ControladorRepartidores controladorRepartidores;
    private final ControladorEntregas controladorEntregas;
    private final Eventos eventos;
    private final Runnable observador = this::cargarCombos;

    private final JComboBox<Pedido> cmbPedidos = new JComboBox<>();
    private final JComboBox<Repartidor> cmbRepartidores = new JComboBox<>();
    private final JButton btnIniciar = Estilo.boton("Iniciar entrega");
    private final JLabel lblEstado = new JLabel(" ");

    public VentanaAsignarRepartidor(ControladorPedidos controladorPedidos,
                                    ControladorRepartidores controladorRepartidores,
                                    ControladorEntregas controladorEntregas, Eventos eventos) {
        this.controladorPedidos = controladorPedidos;
        this.controladorRepartidores = controladorRepartidores;
        this.controladorEntregas = controladorEntregas;
        this.eventos = eventos;

        setTitle("SpeedFast - Asignar repartidor");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);

        crearComponentes();
        cargarCombos();
        pack();
        setLocationRelativeTo(null);

        eventos.agregarObservador(observador);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                eventos.quitarObservador(observador);
            }
        });
    }

    private void crearComponentes() {
        JPanel raiz = new JPanel(new BorderLayout(10, 10));
        raiz.setBorder(Estilo.margen(12));
        raiz.add(Estilo.encabezado("Asignar repartidor",
                "Solo se muestran pedidos pendientes y repartidores libres"), BorderLayout.NORTH);

        cmbPedidos.setPreferredSize(new Dimension(320, cmbPedidos.getPreferredSize().height));

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 10));
        form.add(new JLabel("Pedido pendiente:", SwingConstants.RIGHT));
        form.add(cmbPedidos);
        form.add(new JLabel("Repartidor disponible:", SwingConstants.RIGHT));
        form.add(cmbRepartidores);
        raiz.add(form, BorderLayout.CENTER);

        JButton btnCerrar = Estilo.boton("Cerrar");
        JPanel sur = new JPanel(new BorderLayout());
        lblEstado.setForeground(Color.DARK_GRAY);
        sur.add(lblEstado, BorderLayout.WEST);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.add(btnCerrar);
        botones.add(btnIniciar);
        sur.add(botones, BorderLayout.EAST);
        raiz.add(sur, BorderLayout.SOUTH);

        setContentPane(raiz);

        btnIniciar.addActionListener(e -> iniciarEntrega());
        btnCerrar.addActionListener(e -> dispose());
    }

    /** Recarga los combos (pedidos pendientes desde MySQL) manteniendo la selección si sigue siendo válida. */
    private void cargarCombos() {
        Object pedidoSel = cmbPedidos.getSelectedItem();
        Object repSel = cmbRepartidores.getSelectedItem();

        try {
            cmbPedidos.removeAllItems();
            for (Pedido p : controladorPedidos.pendientes()) {
                cmbPedidos.addItem(p);
            }
            cmbRepartidores.removeAllItems();
            for (Repartidor r : controladorRepartidores.disponibles()) {
                cmbRepartidores.addItem(r);
            }
        } catch (DAOException e) {
            btnIniciar.setEnabled(false);
            lblEstado.setText("Sin conexión con la base de datos.");
            return;
        }
        // Se conserva la selección comparando por id (los objetos se vuelven a leer desde MySQL)
        if (pedidoSel != null) {
            for (int i = 0; i < cmbPedidos.getItemCount(); i++) {
                if (cmbPedidos.getItemAt(i).getId() == ((Pedido) pedidoSel).getId()) {
                    cmbPedidos.setSelectedIndex(i);
                }
            }
        }
        if (repSel != null) {
            for (int i = 0; i < cmbRepartidores.getItemCount(); i++) {
                if (cmbRepartidores.getItemAt(i).getId() == ((Repartidor) repSel).getId()) {
                    cmbRepartidores.setSelectedIndex(i);
                }
            }
        }

        boolean hayPedidos = cmbPedidos.getItemCount() > 0;
        boolean hayRepartidores = cmbRepartidores.getItemCount() > 0;
        btnIniciar.setEnabled(hayPedidos && hayRepartidores);

        if (!hayPedidos) {
            lblEstado.setText("No hay pedidos pendientes.");
        } else if (!hayRepartidores) {
            lblEstado.setText("No hay repartidores libres (registre uno o espere una entrega).");
        } else {
            lblEstado.setText(cmbPedidos.getItemCount() + " pendiente(s), "
                    + cmbRepartidores.getItemCount() + " repartidor(es) libre(s).");
        }
    }

    private void iniciarEntrega() {
        Pedido pedido = (Pedido) cmbPedidos.getSelectedItem();
        Repartidor repartidor = (Repartidor) cmbRepartidores.getSelectedItem();
        try {
            controladorEntregas.iniciarEntregaSimulada(pedido, repartidor);
            JOptionPane.showMessageDialog(this,
                    repartidor.getNombre() + " inició la entrega del pedido #" + pedido.getId() + ".\n"
                            + "Tiempo estimado: " + pedido.calcularTiempoEntrega() + " min.",
                    "Entrega iniciada", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            Mensajes.advertencia(this, ex.getMessage());
        } catch (DAOException ex) {
            Mensajes.errorBD(this, ex.getMessage());
        }
    }
}
