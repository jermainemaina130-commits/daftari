CREATE DATABASE IF NOT EXISTS daftari CHARACTER SET utf8mb4;
USE daftari;

CREATE TABLE shop (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  location VARCHAR(100)
);
CREATE TABLE users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  shop_id INT NOT NULL,
  name VARCHAR(100) NOT NULL,
  phone VARCHAR(15) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role ENUM('owner','attendant') NOT NULL,
  FOREIGN KEY (shop_id) REFERENCES shop(id)
);
CREATE TABLE product (
  id INT AUTO_INCREMENT PRIMARY KEY,
  shop_id INT NOT NULL,
  name VARCHAR(100) NOT NULL,
  buy_price DECIMAL(10,2) NOT NULL,
  sell_price DECIMAL(10,2) NOT NULL,
  unit ENUM('unit','packet','heap') NOT NULL DEFAULT 'unit',
  quantity INT NOT NULL DEFAULT 0,
  restock_days INT NOT NULL DEFAULT 2,
  FOREIGN KEY (shop_id) REFERENCES shop(id)
);
CREATE TABLE customer (
  id INT AUTO_INCREMENT PRIMARY KEY,
  shop_id INT NOT NULL,
  name VARCHAR(100) NOT NULL,
  phone VARCHAR(15),
  balance DECIMAL(10,2) NOT NULL DEFAULT 0,
  FOREIGN KEY (shop_id) REFERENCES shop(id)
);
CREATE TABLE sale (
  id INT AUTO_INCREMENT PRIMARY KEY,
  shop_id INT NOT NULL,
  user_id INT NOT NULL,
  sold_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  pay_type ENUM('cash','mpesa','credit') NOT NULL,
  total DECIMAL(10,2) NOT NULL,
  customer_id INT NULL,
  status ENUM('ok','cancelled') NOT NULL DEFAULT 'ok',
  FOREIGN KEY (shop_id) REFERENCES shop(id),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (customer_id) REFERENCES customer(id)
);
CREATE TABLE sale_item (
  id INT AUTO_INCREMENT PRIMARY KEY,
  sale_id INT NOT NULL,
  product_id INT NOT NULL,
  quantity INT NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  FOREIGN KEY (sale_id) REFERENCES sale(id),
  FOREIGN KEY (product_id) REFERENCES product(id)
);
-- sale_id NULL = unmatched. code NULL = message could not be read (FR-20).
-- UNIQUE code stops the same payment being saved twice (FR-19).
CREATE TABLE mpesa_payment (
  id INT AUTO_INCREMENT PRIMARY KEY,
  shop_id INT NOT NULL,
  code VARCHAR(10) UNIQUE,
  amount DECIMAL(10,2),
  sender VARCHAR(100),
  paid_at DATETIME,
  raw TEXT NOT NULL,
  sale_id INT NULL,
  FOREIGN KEY (shop_id) REFERENCES shop(id),
  FOREIGN KEY (sale_id) REFERENCES sale(id)
);
CREATE TABLE credit_entry (
  id INT AUTO_INCREMENT PRIMARY KEY,
  customer_id INT NOT NULL,
  sale_id INT NULL,
  amount DECIMAL(10,2) NOT NULL,
  kind ENUM('sale','payment') NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (customer_id) REFERENCES customer(id)
);
CREATE TABLE stock_count (
  id INT AUTO_INCREMENT PRIMARY KEY,
  product_id INT NOT NULL,
  counted_on DATE NOT NULL,
  expected INT NOT NULL,
  counted INT NOT NULL,
  FOREIGN KEY (product_id) REFERENCES product(id)
);
