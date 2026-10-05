package vista;

import controlador.ControladorPedidos;
import controlador.Eventos;
import dao.DAOException;
import modelo.EstadoPedido;
import modelo.Pedido;
import modelo.TipoPedido;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * VISTA: CRUD de pedidos. Registrar (dirección, tipo y estado), editar y eliminar pedidos en
 * MySQL a través de ControladorPedidos (que usa PedidoDAO), y listarlos en un JTable con
 * filtros opcionales por estado y por tipo.
 *
 * Al seleccionar una fila se carga en el formulario (modo edición). Los filtros vuelven a leer
 * la base de datos cada vez que cambian. La vista no contiene SQL.
 *
 * @author Rodolfo Delgado
 */
public class VentanaPedidos extends JFrame {

    private static final String[] COLUMNAS =
            {"ID", "Dirección", "Tipo", "Min. estimados", "Estado", "Repartidor"};
    private static final String TODOS = "Todos";

    private final ControladorPedidos controlador;
    private final Eventos eventos;
    private final Runnable observador = this::cargarPedidos;

    // Formulario
    private final JTextField txtId = new JTextField(6);
    private final JTextField txtDireccion = new JTextField(26);
    private final JComboBox<TipoPedido> cmbTipo = new JComboBox<>(TipoPedido.values());
    private final JComboBox<EstadoPedido> cmbEstado = new JComboBox<>(EstadoPedido.values());

    // Filtros opcionales ("Todos" = sin filtro)
    private final JComboBox<Object> cmbFiltroEstado = crearComboFiltro(EstadoPedido.values());
    private final JComboBox<Object> cmbFiltroTipo = crearComboFiltro(TipoPedido.values());

    // Tabla
    private final DefaultTableModel modeloTabla = Estilo.modeloSoloLectura(COLUMNAS);
    private final JTable tabla = Estilo.tabla(modeloTabla, 0, 2, 3, 4, 5);
    private final JLabel lblTotal = new JLabel();

    private final JButton btnRegistrar = Estilo.boton("Registrar");
    private final JButton btnActualizar = Estilo.boton("Actualizar");
    private final JButton btnEliminar = Estilo.boton("Eliminar");
    private final JButton btnLimpiar = Estilo.boton("Limpiar");

    private int idSeleccionado = 0;      // 0 = ninguno (modo nuevo registro)
    private boolean recargando = false;  // evita reaccionar a la selección mientras se llena la tabla

    public VentanaPedidos(ControladorPedidos controlador, Eventos eventos) {
        this.controlador = controlador;
        this.eventos = eventos;

        setTitle("SpeedFast - Pedidos");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(860, 560);
        setLocationRelativeTo(null);

        crearComponentes();
        cargarPedidos();
        actualizarModo();

        eventos.agregarObservador(observador);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                eventos.quitarObservador(observador);
            }
        });
    }

    /** Combo con la opción "Todos" al inicio, seguida de los valores del enum. */
    private static JComboBox<Object> crearComboFiltro(Object[] valores) {
        JComboBox<Object> combo = new JComboBox<>();
        combo.addItem(TODOS);
        for (Object v : valores) {
            combo.addItem(v);
        }
        return combo;
    }

    private void crearComponentes() {
        JPanel raiz = new JPanel(new BorderLayout(10, 10));
        raiz.setBorder(Estilo.margen(10));
        raiz.add(Estilo.encabezado("Pedidos",
                "Registrar, editar, eliminar y filtrar (tabla pedidos de MySQL)"), BorderLayout.NORTH);

        // --- Formulario (GridBagLayout: etiquetas a la derecha, campos a la izquierda) ---
        txtId.setEditable(false);
        txtId.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel campos = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        agregarFila(campos, c, 0, "ID:", txtId);
        agregarFila(campos, c, 1, "Dirección de entrega:", txtDireccion);
        agregarFila(campos, c, 2, "Tipo de pedido:", cmbTipo);
        agregarFila(campos, c, 3, "Estado:", cmbEstado);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        botones.add(btnRegistrar);
        botones.add(btnActualizar);
        botones.add(btnEliminar);
        botones.add(btnLimpiar);

        JPanel form = new JPanel(new BorderLayout());
        form.setBorder(BorderFactory.createTitledBorder("Datos del pedido"));
        form.add(campos, BorderLayout.CENTER);
        form.add(botones, BorderLayout.SOUTH);

        // --- Filtros ---
        JButton btnQuitarFiltros = Estilo.boton("Quitar filtros");
        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filtros.setBorder(BorderFactory.createTitledBorder("Filtros opcionales"));
        filtros.add(new JLabel("Estado:"));
        filtros.add(cmbFiltroEstado);
        filtros.add(new JLabel("Tipo:"));
        filtros.add(cmbFiltroTipo);
        filtros.add(btnQuitarFiltros);

        // --- Tabla ---
        tabla.getColumnModel().getColumn(0).setPreferredWidth(50);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(300);

        JPanel arriba = new JPanel(new BorderLayout(0, 6));
        arriba.add(form, BorderLayout.NORTH);
        arriba.add(filtros, BorderLayout.SOUTH);

        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.add(arriba, BorderLayout.NORTH);
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
        btnQuitarFiltros.addActionListener(e -> {
            cmbFiltroEstado.setSelectedIndex(0);
            cmbFiltroTipo.setSelectedIndex(0);
        });
        cmbFiltroEstado.addActionListener(e -> cargarPedidos());
        cmbFiltroTipo.addActionListener(e -> cargarPedidos());
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !recargando) {
                cargarFormularioDesdeTabla();
            }
        });
    }

    private void agregarFila(JPanel panel, GridBagConstraints c, int fila, String etiqueta, JComponent campo) {
        c.gridy = fila;
        c.gridx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel(etiqueta), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.WEST;
        panel.add(campo, c);
    }

    // ==================================================================
    // Acciones CRUD
    // ==================================================================

    private void registrar() {
        try {
            Pedido p = controlador.crear(txtDireccion.getText(),
                    (TipoPedido) cmbTipo.getSelectedItem(),
                    (EstadoPedido) cmbEstado.getSelectedItem());
            Mensajes.exito(this, "Pedido registrado",
                    "Pedido #" + p.getId() + " guardado en la base de datos.\n"
                            + "Tipo: " + p.getTipo() + "  |  Tiempo estimado: " + p.calcularTiempoEntrega() + " min");
            limpiar();
        } catch (IllegalArgumentException ex) {
            advertir(ex);
        } catch (DAOException ex) {
            Mensajes.errorBD(this, ex.getMessage());
        }
    }

    private void actualizar() {
        try {
            Pedido p = controlador.actualizar(idSeleccionado, txtDireccion.getText(),
                    (TipoPedido) cmbTipo.getSelectedItem(),
                    (EstadoPedido) cmbEstado.getSelectedItem());
            Mensajes.exito(this, "Pedido actualizado", "El pedido #" + p.getId() + " fue actualizado.");
        } catch (IllegalArgumentException ex) {
            advertir(ex);
        } catch (DAOException ex) {
            Mensajes.errorBD(this, ex.getMessage());
        }
    }

    private void eliminar() {
        if (idSeleccionado <= 0) {
            Mensajes.advertencia(this, "Seleccione un pedido de la tabla.");
            return;
        }
        if (!Mensajes.confirmar(this, "¿Eliminar el pedido #" + idSeleccionado + "?\n"
                + "Esta acción no se puede deshacer.")) {
            return;
        }
        try {
            controlador.eliminar(idSeleccionado);
            Mensajes.exito(this, "Pedido eliminado", "El pedido fue eliminado de la base de datos.");
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
        txtDireccion.setText("");
        cmbTipo.setSelectedIndex(0);
        cmbEstado.setSelectedItem(EstadoPedido.PENDIENTE);
        actualizarModo();
        txtDireccion.requestFocus();
    }

    private void advertir(IllegalArgumentException ex) {
        Mensajes.advertencia(this, ex.getMessage());
        txtDireccion.requestFocus();
        txtDireccion.selectAll();
    }

    // ==================================================================
    // Tabla <-> formulario
    // ==================================================================

    private void cargarFormularioDesdeTabla() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            idSeleccionado = 0;
            txtId.setText("");
        } else {
            int m = tabla.convertRowIndexToModel(fila);
            idSeleccionado = (int) modeloTabla.getValueAt(m, 0);
            txtId.setText(String.valueOf(idSeleccionado));
            txtDireccion.setText((String) modeloTabla.getValueAt(m, 1));
            cmbTipo.setSelectedItem(modeloTabla.getValueAt(m, 2));
            cmbEstado.setSelectedItem(modeloTabla.getValueAt(m, 4));
        }
        actualizarModo();
    }

    private void actualizarModo() {
        boolean editando = idSeleccionado > 0;
        btnRegistrar.setEnabled(!editando);
        btnActualizar.setEnabled(editando);
        btnEliminar.setEnabled(editando);
    }

    /** Lee los pedidos desde MySQL aplicando los filtros elegidos y llena la tabla. */
    private void cargarPedidos() {
        int idPrevio = idSeleccionado;
        recargando = true;
        try {
            EstadoPedido estado = cmbFiltroEstado.getSelectedItem() instanceof EstadoPedido
                    ? (EstadoPedido) cmbFiltroEstado.getSelectedItem() : null;
            TipoPedido tipo = cmbFiltroTipo.getSelectedItem() instanceof TipoPedido
                    ? (TipoPedido) cmbFiltroTipo.getSelectedItem() : null;

            modeloTabla.setRowCount(0);
            for (Pedido p : controlador.listar(estado, tipo)) {
                modeloTabla.addRow(new Object[]{
                        p.getId(),
                        p.getDireccionEntrega(),
                        p.getTipo(),
                        p.calcularTiempoEntrega(),
                        p.getEstado(),
                        p.getRepartidorAsignado() == null ? "-" : p.getRepartidorAsignado()
                });
            }
            lblTotal.setText("Pedidos mostrados: " + modeloTabla.getRowCount()
                    + (estado != null || tipo != null ? "  (filtrados)" : ""));

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
                idSeleccionado = 0;   // el pedido ya no está en la lista (eliminado o filtrado)
                txtId.setText("");
            }
        } catch (DAOException ex) {
            lblTotal.setText("No se pudieron leer los pedidos de la base de datos.");
            Mensajes.errorBD(this, ex.getMessage());
        } finally {
            recargando = false;
            actualizarModo();
        }
    }
}
