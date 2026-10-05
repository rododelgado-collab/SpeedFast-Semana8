-- ============================================================
-- SpeedFast - Semana 8 (CRUD con patrón DAO + JDBC + MySQL)
-- Base de datos EXCLUSIVA para las pruebas unitarias de los DAO: speedfast_test.
-- Tiene las mismas 3 tablas que speedfast_db, pero las pruebas la vacían antes de cada test,
-- así que no se mezcla con los datos reales de la aplicación.
-- Ejecutar completo en MySQL Workbench (o: mysql -u root -p < script_estructura_test.sql)
-- ============================================================

CREATE DATABASE IF NOT EXISTS speedfast_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE speedfast_test;

-- Un repartidor puede realizar muchas entregas.
CREATE TABLE IF NOT EXISTS repartidores (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL
);

-- Un pedido puede tener una o varias entregas (intentos / etapas).
CREATE TABLE IF NOT EXISTS pedidos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    direccion VARCHAR(100) NOT NULL,
    tipo ENUM('COMIDA','ENCOMIENDA','EXPRESS') NOT NULL,
    estado ENUM('PENDIENTE','EN_REPARTO','ENTREGADO') NOT NULL DEFAULT 'PENDIENTE'
);

-- Cada entrega asocia un pedido con un repartidor, con fecha y hora.
-- Las claves foráneas impiden borrar un pedido o un repartidor que tenga entregas.
CREATE TABLE IF NOT EXISTS entregas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    id_pedido INT NOT NULL,
    id_repartidor INT NOT NULL,
    fecha DATE NOT NULL,
    hora TIME NOT NULL,
    FOREIGN KEY (id_pedido) REFERENCES pedidos(id),
    FOREIGN KEY (id_repartidor) REFERENCES repartidores(id)
);

-- Verificación: deben aparecer repartidores, pedidos y entregas.
SHOW TABLES;
