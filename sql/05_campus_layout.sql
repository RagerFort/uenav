-- UE Caloocan campus layout, checked by a student (2026-11-15). Positions come from a satellite photo and
-- OpenStreetMap building outlines, turned into the app's map frame with a four-corner fit; distances are measured
-- on the photo (metres). Changes: Tan Yan Kee, CFAD, Administration, Gym, Old Academic, Lucio Tan and the gates
-- moved to where they really are; Open Court (was "Beach Volleyball Court"); Parking added; the path from the
-- Samson Road Gate goes between CFAD and the Administration Building, and paths cross the grass field to
-- Lucio Tan and Engineering. The Engineering building and its rooms are unchanged.
-- Run after 04_engineering_building.sql (and again whenever 04 is run again), then 06_tan_yan_kee.sql.
-- Then copy GET /api/snapshot into uenav-mobile/src/main/resources/campus-snapshot.json before building the APK.

USE uenav;

UPDATE building SET map_x = 509, map_y = 577, description = 'Position from a satellite photo and OpenStreetMap, checked by a student' WHERE building_id = 2;
UPDATE building SET map_x = 875, map_y = 698, description = 'Position from a satellite photo and OpenStreetMap, checked by a student' WHERE building_id = 3;
UPDATE building SET map_x = 1041, map_y = 198, description = 'Position from a satellite photo and OpenStreetMap, checked by a student' WHERE building_id = 4;
UPDATE building SET map_x = 689, map_y = 198, description = 'Position from a satellite photo and OpenStreetMap, checked by a student' WHERE building_id = 5;
UPDATE building SET map_x = 378, map_y = 304, description = 'Position from a satellite photo and OpenStreetMap, checked by a student' WHERE building_id = 6;
UPDATE building SET map_x = 1182, map_y = 676, description = 'Position from a satellite photo and OpenStreetMap, checked by a student' WHERE building_id = 8;
UPDATE building SET map_x = 268, map_y = 564, description = 'Position from a satellite photo and OpenStreetMap, checked by a student' WHERE building_id = 9;
UPDATE building SET description = 'Position from a satellite photo and OpenStreetMap, checked by a student' WHERE building_id = 1;

INSERT INTO node (node_id, building_id, floor_id, name, node_type, x, y) VALUES
  (1, NULL, NULL, 'University Hills Gate', 'GATE', 208, 687),
  (2, NULL, NULL, 'Samson Road Gate', 'GATE', 514, 801),
  (3, NULL, NULL, 'University Hills Gate driveway', 'JUNCTION', 330, 702),
  (4, NULL, NULL, 'Gym covered walk', 'JUNCTION', 345, 640),
  (5, NULL, NULL, 'Samson Road Gate walkway', 'JUNCTION', 540, 748),
  (6, NULL, NULL, 'Parking south walkway', 'JUNCTION', 480, 712),
  (7, NULL, NULL, 'Gym covered walk (north)', 'JUNCTION', 338, 450),
  (8, NULL, NULL, 'Field northwest corner', 'JUNCTION', 585, 300),
  (9, NULL, NULL, 'North walk (Old Academic)', 'JUNCTION', 690, 264),
  (10, NULL, NULL, 'North walk (Lucio Tan)', 'JUNCTION', 1040, 268),
  (11, NULL, NULL, 'Field northeast corner', 'JUNCTION', 1222, 262),
  (12, NULL, NULL, 'East walk (Engineering)', 'JUNCTION', 1245, 380),
  (13, NULL, NULL, 'Field southeast corner', 'JUNCTION', 1255, 560),
  (14, NULL, NULL, 'South walk (HRM)', 'JUNCTION', 1180, 615),
  (15, NULL, NULL, 'South walk (Tan Yan Kee)', 'JUNCTION', 850, 620),
  (16, NULL, NULL, 'Field southwest corner', 'JUNCTION', 622, 622),
  (17, NULL, NULL, 'West walk (Registrar)', 'JUNCTION', 540, 480),
  (18, 9, NULL, 'Gym', 'FACILITY', 334, 600),
  (19, 2, NULL, 'Admissions Office', 'OFFICE', 440, 560),
  (20, 2, NULL, 'Cashier''s Office', 'OFFICE', 525, 615),
  (21, 2, NULL, 'Administration Building', 'BUILDING_ENTRANCE', 595, 655),
  (22, 2, NULL, 'Registrar''s Office', 'OFFICE', 515, 545),
  (23, 2, NULL, 'Comptroller''s Office', 'OFFICE', 565, 640),
  (24, NULL, NULL, 'Security Office', 'OFFICE', 565, 777),
  (25, NULL, NULL, 'Gazebo', 'FACILITY', 501, 275),
  (26, NULL, NULL, 'Playground', 'FACILITY', 495, 225),
  (27, 6, NULL, 'Old Fine Arts Building', 'BUILDING_ENTRANCE', 452, 362),
  (28, 5, NULL, 'Old Academic Building', 'BUILDING_ENTRANCE', 690, 240),
  (29, 4, NULL, 'Dr. Lucio C. Tan Building', 'BUILDING_ENTRANCE', 1040, 256),
  (30, NULL, NULL, 'Field', 'FACILITY', 840, 440),
  (31, NULL, NULL, 'Open Court', 'FACILITY', 1125, 465),
  (32, 8, NULL, 'HRM Mock Hotel', 'BUILDING_ENTRANCE', 1180, 636),
  (33, 3, NULL, 'Tan Yan Kee Academic Building', 'BUILDING_ENTRANCE', 850, 660),
  (34, 3, NULL, 'CFAD Building (Tan Yan Kee west wing)', 'BUILDING_ENTRANCE', 598, 672),
  (400, NULL, NULL, 'Walkway between CFAD and Administration', 'JUNCTION', 570, 700),
  (401, NULL, NULL, 'Parking', 'FACILITY', 415, 643),
  (402, NULL, NULL, 'Old Fine Arts walkway', 'JUNCTION', 455, 415)
ON DUPLICATE KEY UPDATE building_id = VALUES(building_id), floor_id = VALUES(floor_id), name = VALUES(name),
  node_type = VALUES(node_type), x = VALUES(x), y = VALUES(y);

-- every walkway that does not touch the Engineering building is rebuilt (04 owns those)
DELETE FROM edge WHERE from_node_id NOT IN (SELECT node_id FROM (SELECT node_id FROM node WHERE building_id = 1) b)
                   AND to_node_id NOT IN (SELECT node_id FROM (SELECT node_id FROM node WHERE building_id = 1) b);
INSERT INTO edge (edge_id, from_node_id, to_node_id, distance_m, edge_type, is_accessible) VALUES
  (1, 1, 3, 29.4, 'WALK', TRUE),
  (2, 3, 4, 16.6, 'WALK', TRUE),
  (3, 3, 6, 37.4, 'WALK', TRUE),
  (4, 6, 5, 17.0, 'WALK', TRUE),
  (5, 5, 2, 16.4, 'WALK', TRUE),
  (6, 5, 24, 9.5, 'WALK', TRUE),
  (7, 6, 401, 22.1, 'WALK', TRUE),
  (8, 4, 401, 17.2, 'WALK', TRUE),
  (9, 4, 18, 9.7, 'WALK', TRUE),
  (10, 4, 19, 33.0, 'WALK', TRUE),
  (11, 4, 7, 45.0, 'WALK', TRUE),
  (12, 7, 402, 30.4, 'WALK', TRUE),
  (13, 402, 27, 11.8, 'WALK', TRUE),
  (14, 402, 17, 23.5, 'WALK', TRUE),
  (15, 402, 8, 43.6, 'WALK', TRUE),
  (16, 5, 400, 15.6, 'WALK', TRUE),
  (17, 400, 21, 13.9, 'WALK', TRUE),
  (18, 400, 34, 10.8, 'WALK', TRUE),
  (19, 400, 16, 25.3, 'WALK', TRUE),
  (20, 19, 22, 19.5, 'WALK', TRUE),
  (21, 22, 17, 17.5, 'WALK', TRUE),
  (22, 22, 20, 16.9, 'WALK', TRUE),
  (23, 20, 23, 11.2, 'WALK', TRUE),
  (24, 23, 21, 8.2, 'WALK', TRUE),
  (25, 17, 16, 37.7, 'WALK', TRUE),
  (26, 8, 25, 19.8, 'WALK', TRUE),
  (27, 8, 26, 24.4, 'WALK', TRUE),
  (28, 8, 9, 27.7, 'WALK', TRUE),
  (29, 9, 28, 5.2, 'WALK', TRUE),
  (30, 9, 10, 88.0, 'WALK', TRUE),
  (31, 10, 29, 2.7, 'WALK', TRUE),
  (32, 10, 11, 48.2, 'WALK', TRUE),
  (33, 11, 12, 28.0, 'WALK', TRUE),
  (34, 12, 31, 39.1, 'WALK', TRUE),
  (35, 12, 13, 44.7, 'WALK', TRUE),
  (36, 13, 14, 25.7, 'WALK', TRUE),
  (37, 14, 32, 5.5, 'WALK', TRUE),
  (38, 14, 15, 92.1, 'WALK', TRUE),
  (39, 15, 33, 10.3, 'WALK', TRUE),
  (40, 15, 16, 60.2, 'WALK', TRUE),
  (41, 16, 34, 14.8, 'WALK', TRUE),
  (42, 16, 21, 11.5, 'WALK', TRUE),
  (43, 17, 30, 77.6, 'WALK', TRUE),
  (44, 15, 30, 44.0, 'WALK', TRUE),
  (45, 16, 30, 75.2, 'WALK', TRUE),
  (46, 16, 10, 142.6, 'WALK', TRUE),
  (47, 16, 11, 184.8, 'WALK', TRUE),
  (48, 16, 12, 180.1, 'WALK', TRUE);

-- the two links into the Engineering building (made by 04) get distances from the new corner positions
UPDATE edge SET distance_m = 12.4 WHERE from_node_id = 13 AND to_node_id = 35;
UPDATE edge SET distance_m = 25.1 WHERE from_node_id = 11 AND to_node_id = 162;
