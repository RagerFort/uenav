-- UE Caloocan campus, outdoor places and walkways traced from the official campus map.
-- X/Y use the same frame as the app's 3D map (uenav-mobile/src/main/resources/maps/campus-3d.json):
-- pixels of the campus map with its legend cut off (X to the right, Y downwards).
-- Distances are ESTIMATES: picture length x 0.269 m per pixel (campus area 4.86 ha / outline area).
-- Measure the real walkways and correct distance_m in the admin app (Walkways page).
-- The College of Engineering indoor rooms (1F, 4F, EN 420) are still SAMPLE rows.
-- Run:  mysql -u root -p uenav < sql/02_seed_sample.sql
-- WARNING: this first DELETES the usage log and all map rows (admin accounts are kept).

USE uenav;

DELETE FROM usage_log;
DELETE FROM edge;
DELETE FROM node;
DELETE FROM floor;
DELETE FROM building;

INSERT INTO building (building_id, name, category, description, map_x, map_y) VALUES
  (1, 'College of Engineering Building', 'Building', 'Position from the UE Caloocan campus map; estimated', 1360, 380),
  (2, 'Administration Building', 'Building', 'Position from the UE Caloocan campus map; estimated', 465, 470),
  (3, 'Tan Yan Kee Academic Building', 'Building', 'Position from the UE Caloocan campus map; estimated', 860, 595),
  (4, 'Dr. Lucio C. Tan Building', 'Building', 'Position from the UE Caloocan campus map; estimated', 1040, 155),
  (5, 'Old Academic Building', 'Building', 'Position from the UE Caloocan campus map; estimated', 725, 160),
  (6, 'Old Fine Arts Building', 'Building', 'Position from the UE Caloocan campus map; estimated', 470, 210),
  (7, 'CFAD Building', 'Building', 'Position from the UE Caloocan campus map; estimated', 585, 575),
  (8, 'HRM Mock Hotel', 'Building', 'Position from the UE Caloocan campus map; estimated', 1165, 610),
  (9, 'Gym', 'Facility', 'Position from the UE Caloocan campus map; estimated', 280, 420);

INSERT INTO floor (floor_id, building_id, floor_number, floor_name) VALUES
  (1, 1, 1, '1st floor'),
  (2, 1, 4, '4th floor');

INSERT INTO node (node_id, building_id, floor_id, name, node_type, x, y) VALUES
  (1, NULL, NULL, 'University Hills Gate', 'GATE', 188, 540),
  (2, NULL, NULL, 'Samson Road Gate', 'GATE', 465, 712),
  (3, NULL, NULL, 'Hills Gate walkway', 'JUNCTION', 235, 560),
  (4, NULL, NULL, 'Front plaza', 'JUNCTION', 350, 540),
  (5, NULL, NULL, 'Security walkway', 'JUNCTION', 440, 655),
  (6, NULL, NULL, 'Gym-Admin walkway (south)', 'JUNCTION', 320, 480),
  (7, NULL, NULL, 'Gym-Admin walkway (north)', 'JUNCTION', 425, 300),
  (8, NULL, NULL, 'Field northwest corner', 'JUNCTION', 465, 285),
  (9, NULL, NULL, 'North walk (Old Academic)', 'JUNCTION', 725, 212),
  (10, NULL, NULL, 'North walk (Lucio Tan)', 'JUNCTION', 1040, 205),
  (11, NULL, NULL, 'Field northeast corner', 'JUNCTION', 1240, 210),
  (12, NULL, NULL, 'East walk (Engineering)', 'JUNCTION', 1250, 380),
  (13, NULL, NULL, 'Field southeast corner', 'JUNCTION', 1255, 560),
  (14, NULL, NULL, 'South walk (HRM)', 'JUNCTION', 1160, 565),
  (15, NULL, NULL, 'South walk (Tan Yan Kee)', 'JUNCTION', 860, 560),
  (16, NULL, NULL, 'Field southwest corner', 'JUNCTION', 645, 545),
  (17, NULL, NULL, 'West walk (Registrar)', 'JUNCTION', 520, 375),
  (18, 9, NULL, 'Gym', 'FACILITY', 305, 450),
  (19, 2, NULL, 'Admissions Office', 'OFFICE', 385, 445),
  (20, 2, NULL, 'Cashier''s Office', 'OFFICE', 430, 515),
  (21, 2, NULL, 'Administration Building', 'BUILDING_ENTRANCE', 410, 480),
  (22, 2, NULL, 'Registrar''s Office', 'OFFICE', 505, 395),
  (23, 2, NULL, 'Comptroller''s Office', 'OFFICE', 550, 440),
  (24, NULL, NULL, 'Security Office', 'OFFICE', 465, 640),
  (25, NULL, NULL, 'Gazebo', 'FACILITY', 488, 312),
  (26, NULL, NULL, 'Playground', 'FACILITY', 560, 230),
  (27, 6, NULL, 'Old Fine Arts Building', 'BUILDING_ENTRANCE', 485, 250),
  (28, 5, NULL, 'Old Academic Building', 'BUILDING_ENTRANCE', 725, 195),
  (29, 4, NULL, 'Dr. Lucio C. Tan Building', 'BUILDING_ENTRANCE', 1040, 190),
  (30, NULL, NULL, 'Field', 'FACILITY', 810, 380),
  (31, NULL, NULL, 'Beach Volleyball Court', 'FACILITY', 1160, 370),
  (32, 8, NULL, 'HRM Mock Hotel', 'BUILDING_ENTRANCE', 1160, 585),
  (33, 3, NULL, 'Tan Yan Kee Academic Building', 'BUILDING_ENTRANCE', 860, 575),
  (34, 7, NULL, 'CFAD Building', 'BUILDING_ENTRANCE', 620, 555),
  (35, 1, 1, 'College of Engineering Building', 'BUILDING_ENTRANCE', 1290, 380),
  (36, 1, 1, 'EN lobby (1F)', 'JUNCTION', 1350, 380),
  (37, 1, 1, 'EN stairs (1F)', 'STAIRS', 1360, 300),
  (38, 1, 1, 'EN elevator (1F)', 'ELEVATOR', 1365, 450),
  (39, 1, 2, 'EN stairs (4F)', 'STAIRS', 1360, 300),
  (40, 1, 2, 'EN elevator (4F)', 'ELEVATOR', 1365, 450),
  (41, 1, 2, 'EN 4F hallway', 'JUNCTION', 1360, 380),
  (42, 1, 2, 'EN 420 (4F)', 'ROOM', 1360, 200);

INSERT INTO edge (edge_id, from_node_id, to_node_id, distance_m, edge_type, is_accessible) VALUES
  (1, 1, 3, 13.7, 'WALK', TRUE),
  (2, 3, 4, 31.4, 'WALK', TRUE),
  (3, 4, 5, 39.3, 'WALK', TRUE),
  (4, 5, 2, 16.7, 'WALK', TRUE),
  (5, 3, 6, 31.4, 'WALK', TRUE),
  (6, 4, 6, 18.0, 'WALK', TRUE),
  (7, 6, 18, 9.0, 'WALK', TRUE),
  (8, 6, 7, 56.1, 'WALK', TRUE),
  (9, 7, 8, 11.5, 'WALK', TRUE),
  (10, 4, 19, 27.2, 'WALK', TRUE),
  (11, 4, 21, 22.8, 'WALK', TRUE),
  (12, 21, 20, 10.8, 'WALK', TRUE),
  (13, 19, 21, 11.6, 'WALK', TRUE),
  (14, 5, 24, 7.8, 'WALK', TRUE),
  (15, 8, 25, 9.5, 'WALK', TRUE),
  (16, 8, 26, 29.5, 'WALK', TRUE),
  (17, 8, 27, 10.8, 'WALK', TRUE),
  (18, 26, 9, 44.7, 'WALK', TRUE),
  (19, 9, 28, 4.6, 'WALK', TRUE),
  (20, 9, 10, 84.8, 'WALK', TRUE),
  (21, 10, 29, 4.0, 'WALK', TRUE),
  (22, 10, 11, 53.8, 'WALK', TRUE),
  (23, 11, 12, 45.8, 'WALK', TRUE),
  (24, 12, 13, 48.4, 'WALK', TRUE),
  (25, 13, 14, 25.6, 'WALK', TRUE),
  (26, 14, 15, 80.7, 'WALK', TRUE),
  (27, 15, 16, 58.0, 'WALK', TRUE),
  (28, 16, 17, 56.8, 'WALK', TRUE),
  (29, 17, 8, 28.4, 'WALK', TRUE),
  (30, 17, 22, 6.7, 'WALK', TRUE),
  (31, 17, 23, 19.3, 'WALK', TRUE),
  (32, 16, 34, 7.2, 'WALK', TRUE),
  (33, 15, 33, 4.0, 'WALK', TRUE),
  (34, 14, 32, 5.4, 'WALK', TRUE),
  (35, 12, 31, 24.4, 'WALK', TRUE),
  (36, 17, 30, 78.0, 'WALK', TRUE),
  (37, 15, 30, 50.3, 'WALK', TRUE),
  (38, 12, 35, 10.8, 'WALK', TRUE),
  (39, 35, 36, 16.1, 'WALK', TRUE),
  (40, 36, 37, 21.7, 'WALK', TRUE),
  (41, 36, 38, 19.3, 'WALK', TRUE),
  (42, 39, 41, 21.5, 'WALK', TRUE),
  (43, 40, 41, 18.9, 'WALK', TRUE),
  (44, 41, 42, 48.4, 'WALK', TRUE),
  (45, 37, 39, 25.0, 'STAIRS', FALSE),
  (46, 38, 40, 30.0, 'ELEVATOR', TRUE);
