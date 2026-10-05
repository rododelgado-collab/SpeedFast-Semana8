package controlador;

import dao.DAOException;
import dao.EntregaDAO;
import dao.PedidoDAO;
import dao.RepartidorDAO;
import modelo.Entrega;
import modelo.EstadoPedido;
import modelo.Pedido;
import modelo.Repartidor;
import modelo.TipoPedido;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO "falsos" que guardan todo en memoria. Sirven para probar los controladores (validaciones y
 * reglas) sin necesitar MySQL: así estas pruebas corren siempre, también en un computador sin base de datos.
 */
final class DaosFalsos {

    private DaosFalsos() { }

    /** Cuando vale true, todos los métodos lanzan DAOException (simula MySQL caído). */
    static boolean caidos = false;

    static void revisar() {
        if (caidos) {
            throw new DAOException("No se pudo conectar con el servidor MySQL (simulado).", null);
        }
    }

    static class Repartidores implements RepartidorDAO {
        final Map<Integer, Repartidor> datos = new LinkedHashMap<>();
        int siguiente = 1;

        @Override public void create(Repartidor r) { revisar(); r.setId(siguiente++); datos.put(r.getId(), new Repartidor(r.getId(), r.getNombre())); }
        @Override public List<Repartidor> readAll() {
            revisar();
            List<Repartidor> copia = new ArrayList<>();
            for (Repartidor r : datos.values()) copia.add(new Repartidor(r.getId(), r.getNombre()));
            return copia;
        }
        @Override public Repartidor readById(int id) { revisar(); return datos.get(id); }
        @Override public boolean update(Repartidor r) {
            revisar();
            if (!datos.containsKey(r.getId())) return false;
            datos.put(r.getId(), new Repartidor(r.getId(), r.getNombre()));
            return true;
        }
        @Override public boolean delete(int id) { revisar(); return datos.remove(id) != null; }
    }

    static class Pedidos implements PedidoDAO {
        final Map<Integer, Pedido> datos = new LinkedHashMap<>();
        int siguiente = 1;

        @Override public void create(Pedido p) { revisar(); p.setId(siguiente++); datos.put(p.getId(), copiar(p)); }
        @Override public List<Pedido> readAll() { return readByFiltros(null, null); }
        @Override public List<Pedido> readByFiltros(EstadoPedido estado, TipoPedido tipo) {
            revisar();
            List<Pedido> r = new ArrayList<>();
            for (Pedido p : datos.values()) {
                if ((estado == null || p.getEstado() == estado) && (tipo == null || p.getTipo() == tipo)) r.add(copiar(p));
            }
            return r;
        }
        @Override public Pedido readById(int id) { revisar(); Pedido p = datos.get(id); return p == null ? null : copiar(p); }
        @Override public boolean update(Pedido p) {
            revisar();
            if (!datos.containsKey(p.getId())) return false;
            datos.put(p.getId(), copiar(p));
            return true;
        }
        @Override public boolean updateEstado(int id, EstadoPedido nuevo) {
            revisar();
            Pedido p = datos.get(id);
            if (p == null) return false;
            p.restaurarDesdeBD(nuevo, null);
            return true;
        }
        @Override public boolean delete(int id) { revisar(); return datos.remove(id) != null; }
        @Override public Map<EstadoPedido, Integer> contarPorEstado() {
            revisar();
            Map<EstadoPedido, Integer> m = new EnumMap<>(EstadoPedido.class);
            for (EstadoPedido e : EstadoPedido.values()) m.put(e, 0);
            for (Pedido p : datos.values()) m.merge(p.getEstado(), 1, Integer::sum);
            return m;
        }

        /** Atajo para preparar datos en las pruebas. */
        Pedido agregar(String direccion, TipoPedido tipo, EstadoPedido estado) {
            Pedido p = Pedido.crear(0, direccion, tipo);
            p.restaurarDesdeBD(estado, null);
            create(p);
            return datos.get(p.getId());
        }

        private static Pedido copiar(Pedido p) {
            Pedido c = Pedido.crear(p.getId(), p.getDireccionEntrega(), p.getTipo());
            c.restaurarDesdeBD(p.getEstado(), p.getRepartidorAsignado());
            return c;
        }
    }

    static class Entregas implements EntregaDAO {
        final Map<Integer, Entrega> datos = new LinkedHashMap<>();
        int siguiente = 1;

        @Override public void create(Entrega e) { revisar(); e.setId(siguiente++); datos.put(e.getId(), e); }
        @Override public List<Entrega> readAll() { revisar(); return new ArrayList<>(datos.values()); }
        @Override public List<Entrega> readByPedido(int id) {
            revisar();
            List<Entrega> r = new ArrayList<>();
            for (Entrega e : datos.values()) if (e.getIdPedido() == id) r.add(e);
            return r;
        }
        @Override public List<Entrega> readByRepartidor(int id) {
            revisar();
            List<Entrega> r = new ArrayList<>();
            for (Entrega e : datos.values()) if (e.getIdRepartidor() == id) r.add(e);
            return r;
        }
        @Override public Entrega readById(int id) { revisar(); return datos.get(id); }
        @Override public boolean update(Entrega e) {
            revisar();
            if (!datos.containsKey(e.getId())) return false;
            datos.put(e.getId(), e);
            return true;
        }
        @Override public boolean delete(int id) { revisar(); return datos.remove(id) != null; }
        @Override public int countByPedido(int id) { return readByPedido(id).size(); }
        @Override public int countByRepartidor(int id) { return readByRepartidor(id).size(); }
    }
}
