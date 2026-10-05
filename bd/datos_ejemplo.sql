-- Datos de ejemplo OPCIONALES para probar el CRUD (la aplicación también permite registrarlos
-- desde las ventanas). Ejecutar DESPUÉS de script_estructura.sql.
SET NAMES utf8mb4;   -- que las tildes y eñes se guarden bien aunque el cliente use otra codificación
USE speedfast_db;

INSERT INTO repartidores (nombre) VALUES ('Juan Pérez'), ('María González'), ('Pedro Soto');

INSERT INTO pedidos (direccion, tipo, estado) VALUES
    ('Av. Providencia 1234, Providencia', 'COMIDA',     'PENDIENTE'),
    ('Calle Huérfanos 850, Santiago',     'ENCOMIENDA', 'PENDIENTE'),
    ('Av. Apoquindo 4500, Las Condes',    'EXPRESS',    'PENDIENTE'),
    ('Av. Vicuña Mackenna 2100, Ñuñoa',   'COMIDA',     'ENTREGADO'),
    ('Gran Avenida 5600, San Miguel',     'ENCOMIENDA', 'ENTREGADO');

-- Entregas de los dos pedidos ya entregados (pedidos 4 y 5; repartidores 1 y 2)
INSERT INTO entregas (id_pedido, id_repartidor, fecha, hora)
SELECT p.id, r.id, '2026-10-05', '10:30:00'
FROM pedidos p, repartidores r
WHERE p.direccion LIKE 'Av. Vicuña Mackenna%' AND r.nombre = 'Juan Pérez'
LIMIT 1;

INSERT INTO entregas (id_pedido, id_repartidor, fecha, hora)
SELECT p.id, r.id, '2026-10-05', '11:15:00'
FROM pedidos p, repartidores r
WHERE p.direccion LIKE 'Gran Avenida%' AND r.nombre = 'María González'
LIMIT 1;

SELECT * FROM repartidores;
SELECT * FROM pedidos;
SELECT * FROM entregas;
