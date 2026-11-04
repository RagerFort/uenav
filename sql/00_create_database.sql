-- Run once as a MySQL admin (root). Change the password first.
CREATE DATABASE IF NOT EXISTS uenav CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'uenav_app'@'localhost' IDENTIFIED BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON uenav.* TO 'uenav_app'@'localhost';
FLUSH PRIVILEGES;
-- Then run 01_schema.sql and 02_seed_sample.sql into database uenav.
