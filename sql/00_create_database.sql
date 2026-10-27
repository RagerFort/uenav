-- Run once as a MySQL admin (root). Change the password first.
CREATE DATABASE IF NOT EXISTS uenav CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'uenav_app'@'localhost' IDENTIFIED BY 'patrickalde123';
GRANT ALL PRIVILEGES ON uenav.* TO 'uenav_app'@'localhost';
FLUSH PRIVILEGES;
-- Tables come in T4, after decisions D2 to D4 are settled.
