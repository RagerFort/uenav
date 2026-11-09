-- Upgrade for databases made before 2026-11-09 (D14). Safe to run more than once; keeps all data and the usage log.
--  1. Adds node.description and node.open_hours (the office info card on the phone).
--  2. Fills in SAMPLE descriptions and hours for the offices where they are still empty. Confirm them with each office
--     and correct them in the admin app (Places > Edit).
--  3. Adds two SAMPLE restrooms in the College of Engineering Building (1F and 4F), for the "Nearest restroom" button.
-- Run:  mysql -u root -p uenav < sql/03_place_details.sql
-- Then copy GET /api/snapshot into uenav-mobile/src/main/resources/campus-snapshot.json before building the APK.

USE uenav;

-- 1. columns (MySQL 8 has no ADD COLUMN IF NOT EXISTS, so check first)
SET @missing := (SELECT COUNT(*) = 0 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'node' AND column_name = 'description');
SET @ddl := IF(@missing, 'ALTER TABLE node ADD COLUMN description VARCHAR(500) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @missing := (SELECT COUNT(*) = 0 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'node' AND column_name = 'open_hours');
SET @ddl := IF(@missing, 'ALTER TABLE node ADD COLUMN open_hours VARCHAR(100) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. SAMPLE office info (only where nothing was entered yet)
UPDATE node SET description = 'Enrollment records, transcripts of records (TOR), certifications and diplomas.'
  WHERE name = 'Registrar''s Office' AND description IS NULL;
UPDATE node SET description = 'Tuition and fee payments, official receipts.'
  WHERE name = 'Cashier''s Office' AND description IS NULL;
UPDATE node SET description = 'Applications for new students, admission requirements and entrance test schedules.'
  WHERE name = 'Admissions Office' AND description IS NULL;
UPDATE node SET description = 'Student account balances, assessments and payment arrangements.'
  WHERE name = 'Comptroller''s Office' AND description IS NULL;
UPDATE node SET description = 'Campus safety, lost and found, visitor and gate passes.'
  WHERE name = 'Security Office' AND description IS NULL;
UPDATE node SET open_hours = 'Mon-Sat 8:00 AM - 5:00 PM (sample, please confirm)'
  WHERE node_type = 'OFFICE' AND open_hours IS NULL
    AND name IN ('Registrar''s Office', 'Cashier''s Office', 'Admissions Office', 'Comptroller''s Office');
UPDATE node SET open_hours = 'Open 24 hours (sample, please confirm)'
  WHERE name = 'Security Office' AND open_hours IS NULL;

-- 3. SAMPLE restrooms in the Engineering building, each joined to its floor's hallway
INSERT INTO node (building_id, floor_id, name, node_type, x, y)
SELECT 1, 1, 'EN restroom (1F)', 'RESTROOM', 1330, 440 FROM DUAL
WHERE EXISTS (SELECT 1 FROM floor WHERE floor_id = 1 AND building_id = 1)
  AND NOT EXISTS (SELECT 1 FROM node WHERE name = 'EN restroom (1F)');

INSERT INTO node (building_id, floor_id, name, node_type, x, y)
SELECT 1, 2, 'EN restroom (4F)', 'RESTROOM', 1330, 440 FROM DUAL
WHERE EXISTS (SELECT 1 FROM floor WHERE floor_id = 2 AND building_id = 1)
  AND NOT EXISTS (SELECT 1 FROM node WHERE name = 'EN restroom (4F)');

INSERT INTO edge (from_node_id, to_node_id, distance_m, edge_type, is_accessible)
SELECT h.node_id, r.node_id, 17.0, 'WALK', TRUE
FROM node h JOIN node r ON h.name = 'EN lobby (1F)' AND r.name = 'EN restroom (1F)'
WHERE NOT EXISTS (SELECT 1 FROM edge e WHERE (e.from_node_id = h.node_id AND e.to_node_id = r.node_id)
                                         OR (e.from_node_id = r.node_id AND e.to_node_id = h.node_id));

INSERT INTO edge (from_node_id, to_node_id, distance_m, edge_type, is_accessible)
SELECT h.node_id, r.node_id, 18.0, 'WALK', TRUE
FROM node h JOIN node r ON h.name = 'EN 4F hallway' AND r.name = 'EN restroom (4F)'
WHERE NOT EXISTS (SELECT 1 FROM edge e WHERE (e.from_node_id = h.node_id AND e.to_node_id = r.node_id)
                                         OR (e.from_node_id = r.node_id AND e.to_node_id = h.node_id));
