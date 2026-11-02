-- SAMPLE DATA ONLY. Same made-up campus as the phone test app. Distances are NOT real.
-- Delete these rows when real campus data is ready.
-- Run:  mysql -u root -p uenav < sql/02_seed_sample.sql
-- No admin account is seeded: it needs a BCrypt hash, which the server will create (task T7).

USE uenav;

INSERT INTO building (building_id, name, category, description) VALUES
  (1, 'College of Engineering Building', 'Building', 'Sample row');

INSERT INTO floor (floor_id, building_id, floor_number, floor_name) VALUES
  (1, 1, 1, '1st floor'),
  (2, 1, 4, '4th floor');

INSERT INTO node (node_id, building_id, floor_id, name, node_type, x, y) VALUES
  (1,  NULL, NULL, 'University Hills Gate',           'GATE',              0,    0),
  (2,  NULL, NULL, 'Campus junction',                 'JUNCTION',          120,  0),
  (3,  NULL, NULL, 'Registrar''s Office',             'OFFICE',            120, -60),
  (4,  1,    1,    'Engineering Building entrance',   'BUILDING_ENTRANCE', 250,  0),
  (5,  1,    1,    'EN lobby (1F)',                   'JUNCTION',          255,  10),
  (6,  1,    1,    'EN stairs (1F)',                  'STAIRS',            260,  20),
  (7,  1,    1,    'EN elevator (1F)',                'ELEVATOR',          270,  10),
  (8,  1,    2,    'EN stairs (4F)',                  'STAIRS',            260,  20),
  (9,  1,    2,    'EN elevator (4F)',                'ELEVATOR',          270,  10),
  (10, 1,    2,    'EN 420 (4F)',                     'ROOM',              300,  10);

INSERT INTO edge (edge_id, from_node_id, to_node_id, distance_m, edge_type, is_accessible) VALUES
  (1,  1, 2, 120, 'WALK',     TRUE),
  (2,  2, 3,  60, 'WALK',     TRUE),
  (3,  2, 4, 130, 'WALK',     TRUE),
  (4,  4, 5,  10, 'WALK',     TRUE),
  (5,  5, 6,  10, 'WALK',     TRUE),
  (6,  5, 7,  15, 'WALK',     TRUE),
  (7,  6, 8,  25, 'STAIRS',   FALSE),
  (8,  7, 9,  30, 'ELEVATOR', TRUE),
  (9,  8, 10, 25, 'WALK',     TRUE),
  (10, 9, 10, 20, 'WALK',     TRUE);

-- Check: expect building 1, floor 2, node 10, edge 10, usage_log 0, admin_account 0.
SELECT (SELECT COUNT(*) FROM building) AS building, (SELECT COUNT(*) FROM floor) AS floor,
       (SELECT COUNT(*) FROM node) AS node, (SELECT COUNT(*) FROM edge) AS edge,
       (SELECT COUNT(*) FROM usage_log) AS usage_log, (SELECT COUNT(*) FROM admin_account) AS admin_account;
