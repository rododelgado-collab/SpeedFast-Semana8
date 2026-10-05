package vista;

import controlador.ControladorRepartidores;
import controlador.Eventos;
import dao.DAOException;
import modelo.Repartidor;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * VISTA: CRUD de repartidores. Registrar, editar y eliminar repartidores en MySQL a través de
 * ControladorRepartidores (que usa RepartidorDAO), y listarlos en un JTable.
 *
 * Al seleccionar una fila de la tabla se carga en el formulario (modo edición): se habilitan
 * Actualizar y Eliminar. "Limpiar" vuelve al modo nuevo registro. La vista no contiene SQL:
 * solo captura eventos, valida con el controlador y muestra los resultados.
 *
 * @author Rodolfo Delgado
 */
public class VentanaRepartidores extends JFrame {

    private static final String[] COLUMNAS = {"ID", "Nombre", "Estado"};

    private final ControladorRepartidores controlador;
    private final Eventos eventos;
    private final Runnable observador = this::cargarRepartidores;

    private final JTextField txtId = new JTextField(6);
    private final JTextField txtNombre = new JTextField(22);
    private final DefaultTableModel modeloTabla = Estilo.modeloSoloLectura(COLUMNAS);
    private final JTable tabla = Estilo.tabla(modeloTabla, 0, 2);
    private final JLabel lblTotal = new JLabel();

    private final JButton btnRegistrar = Estilo.boton("Registrar");
    private final JButton btnActualizar = Estilo.boton("Actualizar");
    private final JButton btnEliminar = Estilo.boton("Eliminar");
    private final JButton btnLimpiar = Estilo.boton("Limpiar");

    private int idSeleccionado = 0;      // 0 = ninguno (modo nuevo registro)
    private boolean recargando = false;  // evita reaccionar a la selección mientras se llena la tabla

    public VentanaRepartidores(ControladorRepartidores controlador, Eventos eventos) {
        this.controlador = controlador;
        this.eventos = eventos;

        setTitle("SpeedFast - Repartidores");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(560, 480);
        setLocationRelativeTo(null);

        crearComponentes();
        cargarRepartidores();
        actualizarModo();

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
        raiz.setBorder(Estilo.margen(10));
        raiz.add(Estilo.encabezado("Repartidores",
                "Registrar, editar y eliminar (tabla repartidores de MySQL)"), BorderLayout.NORTH);

        // --- Formulario ---
        txtId.setEditable(false);
        txtId.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel campos = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        campos.add(new JLabel("ID:"));
        campos.add(txtId);
        campos.add(new JLabel("Nombre:"));
        campos.add(txtNombre);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        botones.add(btnRegistrar);
        botones.add(btnActualizar);
        botones.add(btnEliminar);
        botones.add(btnLimpiar);

        JPanel form = new JPanel(new BorderLayout());
        form.setBorder(BorderFactory.createTitledBorder("Datos del repartidor"));
        form.add(campos, BorderLayout.NORTH);
        form.add(botones, BorderLayout.SOUTH);

        // --- Tabla ---
        tabla.getColumnModel().getColumn(0).setPreferredWidth(50);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(260);

        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.add(form, BorderLayout.NORTH);
        centro.add(new JScrollPane(tabla), BorderLayout.CENTER);
        raiz.add(centro, BorderLayout.CENTER);

        JButton btnCerrar = Estilo.boton("Cerrar");
        JPanel sur = new JPanel(new BorderLayout());
        sur.add(lblTotal, BorderLayout.WEST);
        JPanel cierre = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        cierre.add(btnCerrar);
        sur.add(cierre, BorderLayout.EAST);
        raiz.add(sur, BorderLayout.SOUTH);

        setContentPane(raiz);
        getRootPane().setDefaultButton(btnRegistrar);   // Enter = Registrar

        // --- Eventos ---
        btnRegistrar.addActionListener(e -> registrar());
        btnActualizar.addActionListener(e -> actualizar());
        btnEliminar.addActionListener(e -> eliminar());
        btnLimpiar.addActionListener(e -> limpiar());
        btnCerrar.addActionListener(e -> dispose());
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !recargando) {
                cargarFormularioDesdeTabla();
            }
        });
    }

    // ==================================================================
    // Acciones CRUD
    // ==================================================================

    private void registrar() {
        try {
            Repartidor r = controlador.crear(txtNombre.getText());
            Mensajes.exito(this, "Repartidor registrado",
                    "Repartidor " + r.getNombre() + " guardado con ID " + r.getId() + ".");
            limpiar();
        } catch (IllegalArgumentException ex) {
            advertir(ex);
        } catch (DAOException ex) {
            Mensajes.errorBD(this, ex.getMessage());
        }
    }

    private void actualizar() {
        try {
            Repartidor r = controlador.actualizar(idSeleccionado, txtNombre.getText());
            Mensajes.exito(this, "Repartidor actualizado",
                    "El repartidor #" + r.getId() + " ahora se llama " + r.getNombre() + ".");
        } catch (IllegalArgumentException ex) {
            advertir(ex);
        } catch (DAOException ex) {
            Mensajes.errorBD(this, ex.getMessage());
        }
    }

    private void eliminar() {
        if (idSeleccionado <= 0) {
            Mensajes.advertencia(this, "Seleccione un repartidor de la tabla.");
            return;
        }
        if (!Mensajes.confirmar(this, "¿Eliminar al repartidor " + txtNombre.getText().trim()
                + " (ID " + idSeleccionado + ")?\nEsta acción no se puede deshacer.")) {
            return;
        }
        try {
            controlador.eliminar(idSeleccionado);
            Mensajes.exito(this, "Repartidor eliminado", "El repartidor fue eliminado de la base de datos.");
            limpiar();
        } catch (IllegalArgumentException ex) {
            advertir(ex);
        } catch (DAOException ex) {
            Mensajes.errorBD(this, ex.getMessage());
        }
    }

    private void limpiar() {
        recargando = true;
        tabla.clearSelection();
        recargando = false;
        idSeleccionado = 0;
        txtId.setText("");
        txtNombre.setText("");
        actualizarModo();
        txtNombre.requestFocus();
    }

    private void advertir(IllegalArgumentException ex) {
        Mensajes.advertencia(this, ex.getMessage());
        txtNombre.requestFocus();
        txtNombre.selectAll();
    }

    // ==================================================================
    // Tabla <-> formulario
    // ==================================================================

    /** Al elegir una fila se copian sus datos al formulario (modo edición). */
    private void cargarFormularioDesdeTabla() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            idSeleccionado = 0;
            txtId.setText("");
        } else {
            int filaModelo = tabla.convertRowIndexToModel(fila);
            idSeleccionado = (int) modeloTabla.getValueAt(filaModelo, 0);
            txtId.setText(String.valueOf(idSeleccionado));
            txtNombre.setText((String) modeloTabla.getValueAt(filaModelo, 1));
        }
        actualizarModo();
    }

    /** Registrar solo en modo nuevo; Actualizar y Eliminar solo con un repartidor seleccionado. */
    private void actualizarModo() {
        boolean editando = idSeleccionado > 0;
        btnRegistrar.setEnabled(!editando);
        btnActualizar.setEnabled(editando);
        btnEliminar.setEnabled(editando);
    }

    /** Vacía el modelo y lo vuelve a llenar con lo que devuelve el controlador (SELECT). */
    private void cargarRepartidores() {
        int idPrevio = idSeleccionado;
        recargando = true;
        try {
            modeloTabla.setRowCount(0);
            for (Repartidor r : controlador.listar()) {
                modeloTabla.addRow(new Object[]{
                        r.getId(),
                        r.getNombre(),
                        r.isDisponible() ? "Disponible" : "En ruta"
                });
            }
            lblTotal.setText("Total de repartidores: " + modeloTabla.getRowCount());

            // Mantener la selección si el repartidor sigue existiendo
            boolean encontrado = false;
            for (int i = 0; i < modeloTabla.getRowCount(); i++) {
                if (idPrevio > 0 && (int) modeloTabla.getValueAt(i, 0) == idPrevio) {
                    int filaVista = tabla.convertRowIndexToView(i);
                    tabla.setRowSelectionInterval(filaVista, filaVista);
                    encontrado = true;
                    break;
                }
            }
            if (!encontrado && idPrevio > 0) {
                idSeleccionado = 0;
                txtId.setText("");
            }
        } catch (DAOException ex) {
            lblTotal.setText("No se pudieron leer los repartidores de la base de datos.");
        } finally {
            recargando = false;
            actualizarModo();
        }
    }
}
