# SpeedFast — Semana 8: CRUD completo con patrón DAO, JDBC y Swing

**Asignatura:** Desarrollo Orientado a Objetos II (PRY2203) — Duoc UC
**Actividad sumativa:** Operaciones CRUD con JDBC y MySQL (IL7)
**Estudiante:** Rodolfo Delgado

Aplicación de escritorio Java Swing para la empresa de reparto SpeedFast. En la semana 7 la aplicación ya guardaba
datos en MySQL; ahora permite **gestionar repartidores, pedidos y entregas con el CRUD completo** (crear, leer,
actualizar y eliminar) desde la interfaz gráfica, con validaciones de entrada, mensajes claros y una capa de acceso a
datos organizada con el patrón **DAO** (interfaz + implementación JDBC por entidad).

## Requisitos

- JDK 17 o superior
- IntelliJ IDEA (el proyecto es Maven: se abre con *File → Open* sobre la carpeta o el `pom.xml`)
- MySQL Server 8 y MySQL Workbench (`localhost:3306`)
- El conector JDBC se descarga solo con Maven: `mysql-connector-j 9.4.0` (ver `pom.xml`); JUnit 5 también, solo para las pruebas

## Cómo ejecutar

1. **Crear la base de datos.** En MySQL Workbench abrir `bd/script_estructura.sql` y ejecutarlo completo
   (crea `speedfast_db` con las tablas `repartidores`, `pedidos` y `entregas`).
   *Opcional:* `bd/datos_ejemplo.sql` inserta 3 repartidores, 5 pedidos y 2 entregas para probar los filtros;
   también se puede partir sin datos y registrarlos desde las ventanas.
2. **Configurar el acceso.** Copiar `db.properties.example` como `db.properties` (junto al `pom.xml`) y ajustar los
   tres datos de la conexión (ver [Configuración de la conexión](#configuración-de-la-conexión)). Este archivo no se
   sube a GitHub (`.gitignore`).
3. **Abrir en IntelliJ**, esperar a que Maven descargue las dependencias (*Reload All Maven Projects* si hace falta)
   y ejecutar `src/main/java/main/Main.java`.

Si la conexión falla, al abrir aparece un aviso con la causa (contraseña incorrecta, base inexistente, servicio MySQL
apagado) y la aplicación permite reintentar cuando MySQL esté disponible.

## Configuración de la conexión

Toda la conexión se define en `db.properties`, **no en el código fuente**. La clase `ConexionBD` lee tres claves:

```properties
db.url=jdbc:mysql://localhost:3306/speedfast_db
db.user=root
db.password=tu_contraseña
```

| Clave | Para qué sirve | Valor por defecto si falta |
|-------|----------------|----------------------------|
| `db.url` | Servidor, puerto y base de datos | `jdbc:mysql://localhost:3306/speedfast_db` |
| `db.user` | Usuario de MySQL | `root` |
| `db.password` | Contraseña de ese usuario | (vacía) |

### Archivos que Git no sube (`.gitignore`)

| Archivo | ¿Se sube? | Para qué es |
|---------|-----------|-------------|
| `db.properties` | No (`.gitignore`) | Conexión real a `speedfast_db`, con tu contraseña |
| `db-test.properties` | No (`.gitignore`) | Conexión a `speedfast_test` para las pruebas unitarias, con tu contraseña |
| `db.properties.example` | Sí | Plantilla de `db.properties` |
| `db-test.properties.example` | Sí | Plantilla de `db-test.properties` |
| `target/`, `out/`, `*.class` | No | Salidas de compilación |
| `.idea/workspace.xml`, `.idea/shelf/` | No | Configuración local de IntelliJ |

Regla práctica: **nunca escribir una contraseña real en un archivo que sí se sube**.

## Modelo de datos

Es el esquema del enunciado de la semana 8, en la base `speedfast_db`:

```
repartidores (id PK, nombre VARCHAR(100) NOT NULL)
pedidos      (id PK, direccion VARCHAR(100) NOT NULL,
              tipo   ENUM('COMIDA','ENCOMIENDA','EXPRESS'),
              estado ENUM('PENDIENTE','EN_REPARTO','ENTREGADO'))
entregas     (id PK, id_pedido FK -> pedidos.id, id_repartidor FK -> repartidores.id, fecha DATE, hora TIME)
```

- Los IDs son `AUTO_INCREMENT`: los genera MySQL.
- Un repartidor puede hacer muchas entregas y un pedido puede tener varias (por ejemplo, si un intento se interrumpe y
  se vuelve a asignar).
- Diferencias con la semana 7 (tablas en singular `pedido`, `repartidor`, `entrega`): las tablas ahora van en plural
  y `tipo` y `estado` son `ENUM`, como pide el enunciado. `script_estructura.sql` usa `CREATE ... IF NOT EXISTS` y
  **no borra ni modifica** las tablas de la semana 7: si seguían en `speedfast_db`, quedan intactas, sin usarse.

## Arquitectura por capas

```
vista (Swing)  ->  controlador (validaciones y reglas)  ->  dao (interfaces)  ->  dao.impl (JDBC)  ->  MySQL
                                   ^
                                   └── modelo (Pedido, Repartidor, Entrega, enums)
```

Los controladores dependen de las **interfaces** DAO, no de JDBC. Por eso se pueden probar con DAO falsos en memoria,
sin base de datos (ver [Pruebas unitarias](#pruebas-unitarias)).

## Estructura del proyecto

```
SpeedFast-Semana8/
├── pom.xml
├── db.properties.example         plantilla de conexión (db.properties queda fuera de Git)
├── db-test.properties.example    plantilla para las pruebas (db-test.properties queda fuera de Git)
├── bd/
│   ├── script_estructura.sql        crea speedfast_db y las 3 tablas del enunciado
│   ├── script_estructura_test.sql   crea speedfast_test (misma estructura, solo para las pruebas)
│   └── datos_ejemplo.sql            datos de ejemplo (opcional)
├── src/test/java/
│   ├── dao/            BaseDaoTest, RepartidorDAOTest, PedidoDAOTest, EntregaDAOTest, ConexionBDTest
│   └── controlador/    DaosFalsos, ControladorRepartidoresTest, ControladorPedidosTest, ControladorEntregasTest
└── src/main/java/
    ├── main/         Main                      comprueba la conexión y abre VentanaPrincipal
    ├── modelo/       Pedido (abstracta), PedidoComida, PedidoEncomienda, PedidoExpress,
    │                 Repartidor, Entrega, TipoPedido, EstadoPedido
    ├── dao/          ConexionBD, DAOException, PedidoDAO, RepartidorDAO, EntregaDAO   (interfaces)
    ├── dao/impl/     PedidoDAOImpl, RepartidorDAOImpl, EntregaDAOImpl                 (JDBC)
    ├── controlador/  ControladorPedidos, ControladorRepartidores, ControladorEntregas,
    │                 Eventos (observadores), EnRuta (pedidos con entrega en curso)
    ├── tareas/       TareaEntrega (Runnable)   simula el recorrido en un hilo
    └── vista/        VentanaPrincipal, VentanaPedidos, VentanaRepartidores, VentanaEntregas,
                      VentanaAsignarRepartidor, Mensajes, Estilo
```

## Capa DAO (JDBC)

Cada entidad tiene una interfaz y una implementación. `ConexionBD` abre las conexiones y traduce los errores de MySQL.

| DAO | Métodos |
|-----|---------|
| `RepartidorDAO` | `create`, `readAll`, `readById`, `update`, `delete` |
| `PedidoDAO` | `create`, `readAll`, `readByFiltros(estado, tipo)` (filtros opcionales), `readById`, `update`, `updateEstado`, `delete`, `contarPorEstado` (GROUP BY) |
| `EntregaDAO` | `create`, `readAll` (JOIN con pedidos y repartidores), `readByPedido`, `readByRepartidor`, `readById`, `update`, `delete`, `countByPedido`, `countByRepartidor` |

Buenas prácticas aplicadas:

- Todas las consultas usan `PreparedStatement` con parámetros `?` (nunca se concatena texto): se evita la inyección SQL.
- `Connection`, `PreparedStatement` y `ResultSet` se cierran con `try-with-resources`.
- Cada `SQLException` se captura en el DAO y se relanza como `DAOException` con un mensaje claro; las ventanas lo
  muestran en un `JOptionPane`, sin stack traces para el usuario.
- `ConexionBD.describirError` traduce los códigos más comunes: 1045 (contraseña), 1049 (base inexistente), 1146
  (falta una tabla), 1451 (no se puede eliminar porque otros registros dependen), 1452 (pedido o repartidor
  inexistente), 1406 (texto demasiado largo) y los errores de conexión.

## Ventanas y operaciones CRUD

| Ventana | Qué permite |
|---------|-------------|
| **Repartidores** | Registrar, editar, eliminar y listar repartidores en una `JTable` (con su estado Disponible / En ruta). |
| **Pedidos** | Registrar (dirección, tipo, estado), editar y eliminar pedidos; tabla con filtros opcionales por estado y por tipo. |
| **Entregas** | Registrar, editar y eliminar entregas eligiendo pedido y repartidor en `JComboBox`, con fecha (`AAAA-MM-DD`) y hora (`HH:MM[:SS]`, botón *Ahora*); lista de todas las entregas o filtradas por pedido o por repartidor. |
| **Asignar repartidor / Iniciar entrega** | Simulación con hilos de las semanas anteriores: el pedido pasa a EN_REPARTO, se registra la entrega y al terminar el hilo queda ENTREGADO. |
| **Principal** | Bitácora de actividad y resumen de pedidos por estado. |

Cómo se usan las ventanas: al hacer clic en una fila de la tabla sus datos pasan al formulario y se habilitan
*Actualizar* y *Eliminar*; *Limpiar* vuelve al modo de registro. Eliminar siempre pide confirmación. Todas las tablas
y combos se **refrescan solos** después de cada operación (patrón Observador con la clase `Eventos`); los combos
muestran un texto legible, `id - nombre` o `id - dirección (tipo)`, pero conservan el objeto con su id.

### Validaciones

| Dato | Regla |
|------|-------|
| Nombre del repartidor | Obligatorio, 2 a 100 caracteres, parte con una letra (acepta tildes, eñes, espacios, puntos, apóstrofes y guiones), sin repetir un nombre ya registrado |
| Dirección del pedido | Obligatoria, 5 a 100 caracteres |
| Tipo y estado | Obligatorios (se eligen en un `JComboBox`) |
| Entrega | Pedido y repartidor obligatorios; fecha con formato `AAAA-MM-DD`, que exista en el calendario, **de este año y no posterior a hoy** (una fecha de un año anterior o futura se rechaza); hora con formato `HH:MM` o `HH:MM:SS`. Una entrega antigua que ya estaba guardada se puede seguir editando mientras no se cambie su fecha |
| Editar o eliminar | Debe haber una fila seleccionada; el registro no puede haber desaparecido de la base de datos |

Los espacios sobrantes se limpian antes de guardar. Los mensajes de error indican qué corregir.

### Reglas de integridad

- **No se puede eliminar un pedido o un repartidor que tiene entregas registradas** (clave foránea): la aplicación avisa
  cuántas entregas tiene y pide eliminar primero esas entregas. Eliminar una entrega no toca el pedido ni el repartidor.
- Un pedido o repartidor con una **entrega simulada en curso** no se puede editar ni eliminar hasta que termine.
- Si la aplicación se cierra a mitad de una entrega, ese pedido queda en EN_REPARTO en la base de datos (en la semana 8
  ya no se corrige solo al abrir): se arregla desde la ventana *Pedidos* editando su estado a PENDIENTE.

## Pruebas unitarias

Hay dos tipos de pruebas con JUnit 5:

- **Controladores** (`src/test/java/controlador`): usan DAO falsos en memoria, así que **no necesitan MySQL** y se
  ejecutan siempre. Comprueban las validaciones, las reglas de integridad, la simulación con hilos y que un error de
  la base de datos llegue como `DAOException` (y, al iniciar una entrega, que se deshaga todo).
- **DAO** (`src/test/java/dao`): se ejecutan contra una **base exclusiva para pruebas**, `speedfast_test`, para no tocar
  los datos reales de `speedfast_db`.

Para las pruebas de los DAO:

1. Ejecutar `bd/script_estructura_test.sql` en MySQL Workbench (crea `speedfast_test` con las 3 tablas).
2. Copiar `db-test.properties.example` como `db-test.properties` y escribir la contraseña.
3. Ejecutar `mvn test` (o, en IntelliJ, clic derecho sobre `src/test/java` → *Run All Tests*).

Cada test parte con las tablas vacías. Como las pruebas borran filas, los tests de los DAO solo se ejecutan si la base
conectada se llama `*_test`; en otro caso se **omiten** con un aviso en la consola (no se borra nada).

| Clase de prueba | Qué comprueba |
|-----------------|---------------|
| `RepartidorDAOTest` | CRUD completo; tildes y eñes; nombre más largo que la columna; eliminar con entregas viola la clave foránea |
| `PedidoDAOTest` | CRUD completo; subclase correcta según el tipo; filtros por estado y tipo; repartidor de la última entrega (LEFT JOIN); `contarPorEstado`; SQL malicioso se guarda como texto; eliminar con entregas viola la clave foránea |
| `EntregaDAOTest` | CRUD completo; JOIN con dirección y repartidor y orden; consultas por pedido y por repartidor; pedido o repartidor inexistente viola la clave foránea |
| `ConexionBDTest` | `describirError` traduce 1045, 1049, 1146, 1451, 1452, 1406 y los errores de conexión (no necesita base de datos) |
| `Controlador*Test` | Validaciones, reglas de integridad, reutilización de objetos y errores de la base de datos |

## Continuidad con semanas anteriores

| Semana | Lo que se reutiliza aquí |
|--------|--------------------------|
| 2–4 | Jerarquía `Pedido` abstracta con `PedidoComida`, `PedidoEncomienda`, `PedidoExpress` y `calcularTiempoEntrega()` |
| 4–5 | `EstadoPedido`, cambios de estado `synchronized`, repartidores que entregan en un hilo (`TareaEntrega`) |
| 6 | Interfaz Swing con MVC, observadores y `JTable` con `DefaultTableModel` |
| 7 | `ConexionBD` con `db.properties`, `DAOException`, pruebas contra una base `*_test` |

## Notas sobre el enunciado

- El enunciado menciona `ConexionDB` y una `ClienteDAO`. En este proyecto la clase de conexión se llama `ConexionBD`
  (así se creó en la semana 7) y, como SpeedFast no maneja clientes sino repartidores, la gestión equivalente es
  `RepartidorDAO`.
- Se mantienen `create()`, `readAll()`, `update()` y `delete()` en los tres DAO, tal como se pidió, más consultas
  adicionales (`readById`, filtros y conteos).
