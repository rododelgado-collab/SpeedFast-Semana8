-- ============================================================
-- SpeedFast - Semana 8 (CRUD con patrón DAO + JDBC + MySQL)
-- Script de estructura: base speedfast_db con las tablas repartidores, pedidos y entregas.
-- Ejecutar completo en MySQL Workbench (o por consola: mysql -u root -p < script_estructura.sql)
--
-- Es el esquema del enunciado de la semana 8 (nombres de tablas en plural y columnas ENUM).
-- Usa CREATE ... IF NOT EXISTS, así que se puede ejecutar más de una vez sin errores.
-- Las tablas de la semana 7 (repartidor, pedido, entrega, en singular) no se tocan: pueden
-- seguir existiendo en la misma base sin interferir con estas.
-- ============================================================

CREATE DATABASE IF NOT EXISTS speedfast_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE speedfast_db;

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
