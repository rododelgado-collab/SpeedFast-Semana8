package vista;

import controlador.ControladorEntregas;
import controlador.ControladorPedidos;
import controlador.ControladorRepartidores;
import controlador.Eventos;
import dao.DAOException;
import modelo.Entrega;
import modelo.Pedido;
import modelo.Repartidor;
import modelo.TipoPedido;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * VISTA: CRUD de entregas. Registra una entrega asociando un Pedido y un Repartidor (elegidos en
 * JComboBox cargados desde la base de datos) con fecha y hora, y permite editarla, eliminarla y
 * listarla en un JTable, con filtro por pedido o por repartidor.
 *
 * Los combos muestran un texto legible ("id - dirección" / "id - nombre") pero cada ítem es el
 * objeto Pedido o Repartidor, así que internamente se conserva el id. Los combos se vuelven a
 * cargar cuando se crea, edita o elimina un pedido o un repartidor.
 *
 * @author Rodolfo Delgado
 */
public class VentanaEntregas extends JFrame {

    private static final String[] COLUMNAS = {"ID", "ID pedido", "Dirección", "Repartidor", "Fecha", "Hora"};
    private static final String FILTRO_TODAS = "Todas las entregas";
    private static final String FILTRO_PEDIDO = "Por pedido";
    private static final String FILTRO_REPARTIDOR = "Por repartidor";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ControladorEntregas controlador;
    private final ControladorPedidos controladorPedidos;
    private final ControladorRepartidores controladorRepartidores;
    private final Eventos eventos;
    private final Runnable observador = this::recargarTodo;

    // Formulario
    private final JTextField txtId = new JTextField(6);
    private final JComboBox<Pedido> cmbPedido = new JComboBox<>();
    private final JComboBox<Repartidor> cmbRepartidor = new JComboBox<>();
    private final JTextField txtFecha = new JTextField(10);
    private final JTextField txtHora = new JTextField(8);

    // Filtro de la tabla: tipo de filtro + valor (pedido o repartidor)
    private final JComboBox<String> cmbTipoFiltro =
            new JComboBox<>(new String[]{FILTRO_TODAS, FILTRO_PEDIDO, FILTRO_REPARTIDOR});
    private final JComboBox<Object> cmbValorFiltro = new JComboBox<>();

    // Tabla
    private final DefaultTableModel modeloTabla = Estilo.modeloSoloLectura(COLUMNAS);
    private final JTable tabla = Estilo.tabla(modeloTabla, 0, 1, 4, 5);
    private final JLabel lblTotal = new JLabel();

    private final JButton btnRegistrar = Estilo.boton("Registrar");
    private final JButton btnActualizar = Estilo.boton("Actualizar");
    private final JButton btnEliminar = Estilo.boton("Eliminar");
    private final JButton btnLimpiar = Estilo.boton("Limpiar");

    // Entregas que muestra la tabla ahora: sirven para conocer el id de pedido y de repartidor de cada fila
    private List<Entrega> entregasMostradas = List.of();

    private int idSeleccionado = 0;      // 0 = ninguna (modo nuevo registro)
    private boolean recargando = false;  // evita reaccionar a eventos mientras se llenan combos y tabla

    public VentanaEntregas(ControladorEntregas controlador, ControladorPedidos controladorPedidos,
                           ControladorRepartidores controladorRepartidores, Eventos eventos) {
        this.controlador = controlador;
        this.controladorPedidos = controladorPedidos;
        this.controladorRepartidores = controladorRepartidores;
        this.eventos = eventos;

        setTitle("SpeedFast - Entregas");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        crearComponentes();
        recargarTodo();
        limpiarFechaHora();
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
        raiz.add(Estilo.encabezado("Entregas",
                "Asociar pedido y repartidor con fecha y hora (tabla entregas de MySQL)"), BorderLayout.NORTH);

        // --- Formulario ---
        txtId.setEditable(false);
        txtId.setHorizontalAlignment(SwingConstants.CENTER);
        cmbPedido.setPrototypeDisplayValue(Pedido.crear(99999, "Dirección de ejemplo muy larga 1234, Comuna",
                TipoPedido.ENCOMIENDA));
        cmbRepartidor.setPrototypeDisplayValue(new Repartidor(99999, "Nombre de repartidor largo"));

        JButton btnAhora = Estilo.boton("Ahora");
        btnAhora.setToolTipText("Usar la fecha y la hora actuales");
        JPanel fechaHora = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        fechaHora.add(txtFecha);
        fechaHora.add(new JLabel("(AAAA-MM-DD)"));
        fechaHora.add(Box.createHorizontalStrut(10));
        fechaHora.add(txtHora);
        fechaHora.add(new JLabel("(HH:MM:SS)"));
        fechaHora.add(btnAhora);

        JPanel campos = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        agregarFila(campos, c, 0, "ID:", txtId);
        agregarFila(campos, c, 1, "Pedido:", cmbPedido);
        agregarFila(campos, c, 2, "Repartidor:", cmbRepartidor);
        agregarFila(campos, c, 3, "Fecha y hora:", fechaHora);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        botones.add(btnRegistrar);
        botones.add(btnActualizar);
        botones.add(btnEliminar);
        botones.add(btnLimpiar);

        JPanel form = new JPanel(new BorderLayout());
        form.setBorder(BorderFactory.createTitledBorder("Datos de la entrega"));
        form.add(campos, BorderLayout.CENTER);
        form.add(botones, BorderLayout.SOUTH);

        // --- Filtro ---
        cmbValorFiltro.setPrototypeDisplayValue("Dirección de ejemplo muy larga 1234, Comuna (Encomienda)");
        JPanel filtro = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filtro.setBorder(BorderFactory.createTitledBorder("Filtrar la lista"));
        filtro.add(new JLabel("Mostrar:"));
        filtro.add(cmbTipoFiltro);
        filtro.add(cmbValorFiltro);

        // --- Tabla ---
        tabla.getColumnModel().getColumn(0).setPreferredWidth(50);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(70);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(280);
        tabla.getColumnModel().getColumn(3).setPreferredWidth(140);

        JPanel arriba = new JPanel(new BorderLayout(0, 6));
        arriba.add(form, BorderLayout.NORTH);
        arriba.add(filtro, BorderLayout.SOUTH);

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
        btnAhora.addActionListener(e -> limpiarFechaHora());
        btnCerrar.addActionListener(e -> dispose());
        cmbTipoFiltro.addActionListener(e -> {
            if (!recargando) {
                cargarValoresFiltro();
                cargarEntregas();
            }
        });
        cmbValorFiltro.addActionListener(e -> {
            if (!recargando) {
                cargarEntregas();
            }
        });
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
        c.anchor = GridBagConstraints.WEST;
        panel.add(campo, c);
    }

    // ==================================================================
    // Acciones CRUD
    // ==================================================================

    private void registrar() {
        try {
            Entrega e = controlador.crear((Pedido) cmbPedido.getSelectedItem(),
                    (Repartidor) cmbRepartidor.getSelectedItem(), txtFecha.getText(), txtHora.getText());
            Mensajes.exito(this, "Entrega registrada",
                    "Entrega #" + e.getId() + " guardada: pedido #" + e.getIdPedido()
                            + " con " + cmbRepartidor.getSelectedItem() + ".");
            limpiar();
        } catch (IllegalArgumentException ex) {
            Mensajes.advertencia(this, ex.getMessage());
        } catch (DAOException ex) {
            Mensajes.errorBD(this, ex.getMessage());
        }
    }

    private void actualizar() {
        try {
            controlador.actualizar(idSeleccionado, (Pedido) cmbPedido.getSelectedItem(),
                    (Repartidor) cmbRepartidor.getSelectedItem(), txtFecha.getText(), txtHora.getText());
            Mensajes.exito(this, "Entrega actualizada", "La entrega #" + idSeleccionado + " fue actualizada.");
        } catch (IllegalArgumentException ex) {
            Mensajes.advertencia(this, ex.getMessage());
        } catch (DAOException ex) {
            Mensajes.errorBD(this, ex.getMessage());
        }
    }

    private void eliminar() {
        if (idSeleccionado <= 0) {
            Mensajes.advertencia(this, "Seleccione una entrega de la tabla.");
            return;
        }
        if (!Mensajes.confirmar(this, "¿Eliminar la entrega #" + idSeleccionado + "?\n"
                + "Esta acción no se puede deshacer.")) {
            return;
        }
        try {
            controlador.eliminar(idSeleccionado);
            Mensajes.exito(this, "Entrega eliminada", "La entrega fue eliminada de la base de datos.");
            limpiar();
        } catch (IllegalArgumentException ex) {
            Mensajes.advertencia(this, ex.getMessage());
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
        if (cmbPedido.getItemCount() > 0) {
            cmbPedido.setSelectedIndex(0);
        }
        if (cmbRepartidor.getItemCount() > 0) {
            cmbRepartidor.setSelectedIndex(0);
        }
        limpiarFechaHora();
        actualizarModo();
    }

    /** Deja la fecha y la hora actuales en el formulario. */
    private void limpiarFechaHora() {
        txtFecha.setText(LocalDate.now().toString());
        txtHora.setText(LocalTime.now().withNano(0).format(HORA));
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
            seleccionarPedido((int) modeloTabla.getValueAt(m, 1));
            seleccionarRepartidorPorEntrega(idSeleccionado);
            txtFecha.setText((String) modeloTabla.getValueAt(m, 4));
            txtHora.setText(String.valueOf(modeloTabla.getValueAt(m, 5)));
        }
        actualizarModo();
    }

    private void seleccionarPedido(int idPedido) {
        for (int i = 0; i < cmbPedido.getItemCount(); i++) {
            if (cmbPedido.getItemAt(i).getId() == idPedido) {
                cmbPedido.setSelectedIndex(i);
                return;
            }
        }
    }

    /** La tabla muestra el nombre del repartidor; el combo necesita su id, que está guardado en la lista cargada. */
    private void seleccionarRepartidorPorEntrega(int idEntrega) {
        for (Entrega e : entregasMostradas) {
            if (e.getId() == idEntrega) {
                for (int i = 0; i < cmbRepartidor.getItemCount(); i++) {
                    if (cmbRepartidor.getItemAt(i).getId() == e.getIdRepartidor()) {
                        cmbRepartidor.setSelectedIndex(i);
                        return;
                    }
                }
            }
        }
    }

    private void actualizarModo() {
        boolean editando = idSeleccionado > 0;
        btnRegistrar.setEnabled(!editando);
        btnActualizar.setEnabled(editando);
        btnEliminar.setEnabled(editando);
    }

    // ==================================================================
    // Carga de datos desde MySQL (combos, filtro y tabla)
    // ==================================================================

    /** Recarga combos, valores del filtro y tabla (lo llama el observador al cambiar cualquier dato). */
    private void recargarTodo() {
        recargando = true;
        try {
            // Combos del formulario: se mantiene la selección si el ítem sigue existiendo
            Pedido pedidoSel = (Pedido) cmbPedido.getSelectedItem();
            Repartidor repSel = (Repartidor) cmbRepartidor.getSelectedItem();
            cmbPedido.removeAllItems();
            for (Pedido p : controladorPedidos.listar()) {
                cmbPedido.addItem(p);
            }
            cmbRepartidor.removeAllItems();
            for (Repartidor r : controladorRepartidores.listar()) {
                cmbRepartidor.addItem(r);
            }
            if (pedidoSel != null) {
                seleccionarPedido(pedidoSel.getId());
            }
            if (repSel != null) {
                for (int i = 0; i < cmbRepartidor.getItemCount(); i++) {
                    if (cmbRepartidor.getItemAt(i).getId() == repSel.getId()) {
                        cmbRepartidor.setSelectedIndex(i);
                    }
                }
            }
            cargarValoresFiltro();
        } catch (DAOException ex) {
            lblTotal.setText("No se pudieron leer los datos de la base de datos.");
            Mensajes.errorBD(this, ex.getMessage());
        } finally {
            recargando = false;
        }
        cargarEntregas();
    }

    /** Llena el combo de valores del filtro según el tipo de filtro elegido. */
    private void cargarValoresFiltro() {
        Object previo = cmbValorFiltro.getSelectedItem();
        boolean previaRecargando = recargando;
        recargando = true;
        try {
            cmbValorFiltro.removeAllItems();
            String tipo = (String) cmbTipoFiltro.getSelectedItem();
            if (FILTRO_PEDIDO.equals(tipo)) {
                for (Pedido p : controladorPedidos.listar()) {
                    cmbValorFiltro.addItem(p);
                }
            } else if (FILTRO_REPARTIDOR.equals(tipo)) {
                for (Repartidor r : controladorRepartidores.listar()) {
                    cmbValorFiltro.addItem(r);
                }
            }
            cmbValorFiltro.setEnabled(cmbValorFiltro.getItemCount() > 0);
            // Mantener el valor elegido si sigue existiendo (se compara por id)
            for (int i = 0; i < cmbValorFiltro.getItemCount(); i++) {
                if (mismoId(cmbValorFiltro.getItemAt(i), previo)) {
                    cmbValorFiltro.setSelectedIndex(i);
                    break;
                }
            }
        } finally {
            recargando = previaRecargando;
        }
    }

    private boolean mismoId(Object a, Object b) {
        if (a instanceof Pedido && b instanceof Pedido) {
            return ((Pedido) a).getId() == ((Pedido) b).getId();
        }
        if (a instanceof Repartidor && b instanceof Repartidor) {
            return ((Repartidor) a).getId() == ((Repartidor) b).getId();
        }
        return false;
    }

    /** Lee las entregas (todas, o las del pedido / repartidor elegido en el filtro) y llena la tabla. */
    private void cargarEntregas() {
        int idPrevio = idSeleccionado;
        recargando = true;
        try {
            String tipo = (String) cmbTipoFiltro.getSelectedItem();
            Object valor = cmbValorFiltro.getSelectedItem();
            if (FILTRO_PEDIDO.equals(tipo) && valor instanceof Pedido) {
                entregasMostradas = controlador.listarPorPedido((Pedido) valor);
            } else if (FILTRO_REPARTIDOR.equals(tipo) && valor instanceof Repartidor) {
                entregasMostradas = controlador.listarPorRepartidor((Repartidor) valor);
            } else if (FILTRO_TODAS.equals(tipo)) {
                entregasMostradas = controlador.listar();
            } else {
                entregasMostradas = List.of();   // filtro elegido pero sin pedidos / repartidores que mostrar
            }

            modeloTabla.setRowCount(0);
            for (Entrega e : entregasMostradas) {
                modeloTabla.addRow(new Object[]{
                        e.getId(),
                        e.getIdPedido(),
                        e.getDireccionPedido(),
                        e.getNombreRepartidor(),
                        e.getFecha().toString(),
                        e.getHora().format(HORA)
                });
            }
            lblTotal.setText("Entregas mostradas: " + modeloTabla.getRowCount()
                    + (FILTRO_TODAS.equals(tipo) ? "" : "  (filtradas)"));

            boolean encontrada = false;
            for (int i = 0; i < modeloTabla.getRowCount(); i++) {
                if (idPrevio > 0 && (int) modeloTabla.getValueAt(i, 0) == idPrevio) {
                    int filaVista = tabla.convertRowIndexToView(i);
                    tabla.setRowSelectionInterval(filaVista, filaVista);
                    encontrada = true;
                    break;
                }
            }
            if (!encontrada && idPrevio > 0) {
                idSeleccionado = 0;
                txtId.setText("");
            }
        } catch (DAOException ex) {
            lblTotal.setText("No se pudieron leer las entregas de la base de datos.");
            Mensajes.errorBD(this, ex.getMessage());
        } finally {
            recargando = false;
            actualizarModo();
        }
    }
}
