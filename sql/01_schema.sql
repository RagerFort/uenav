-- UENav schema, MySQL 8.0.16 or newer (CHECK constraints are enforced from 8.0.16).
-- Six tables, following the ERD and docs/decisions.md (D2: rooms are Nodes, no Room table).
-- Run:  mysql -u root -p uenav < sql/01_schema.sql
-- To start over (deletes all data):  DROP DATABASE uenav;  then run 00_create_database.sql again.

USE uenav;

CREATE TABLE admin_account (
  account_id    INT          NOT NULL AUTO_INCREMENT,
  username      VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,            -- BCrypt hash (60 chars), never the password
  full_name     VARCHAR(100) NOT NULL,
  role          VARCHAR(30)  NOT NULL DEFAULT 'ADMIN',
  PRIMARY KEY (account_id),
  UNIQUE KEY uq_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE building (
  building_id INT          NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  category    VARCHAR(50)  NOT NULL,
  description TEXT,
  map_x       INT,
  map_y       INT,
  image_path  VARCHAR(255),
  PRIMARY KEY (building_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE floor (
  floor_id        INT          NOT NULL AUTO_INCREMENT,
  building_id     INT          NOT NULL,
  floor_number    INT          NOT NULL,
  floor_name      VARCHAR(50)  NOT NULL,
  plan_image_path VARCHAR(255),
  PRIMARY KEY (floor_id),
  UNIQUE KEY uq_floor (building_id, floor_number),
  CONSTRAINT fk_floor_building FOREIGN KEY (building_id) REFERENCES building (building_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Node = any point a route can pass through or end at: room, office, gate, junction, stairs, elevator.
-- Per D3 it has a name and X/Y map position. building_id and floor_id are NULL for outdoor nodes.
-- node_type examples: GATE, BUILDING_ENTRANCE, OFFICE, ROOM, JUNCTION, STAIRS, ELEVATOR.
-- By convention JUNCTION, STAIRS and ELEVATOR are not offered in the search list.
CREATE TABLE node (
  node_id     INT          NOT NULL AUTO_INCREMENT,
  building_id INT          NULL,
  floor_id    INT          NULL,
  name        VARCHAR(100) NOT NULL,
  node_type   VARCHAR(30)  NOT NULL,
  x           INT          NOT NULL,
  y           INT          NOT NULL,
  PRIMARY KEY (node_id),
  CONSTRAINT fk_node_building FOREIGN KEY (building_id) REFERENCES building (building_id),
  CONSTRAINT fk_node_floor    FOREIGN KEY (floor_id)    REFERENCES floor (floor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Edge = a link that can be walked in both directions.
CREATE TABLE edge (
  edge_id       INT         NOT NULL AUTO_INCREMENT,
  from_node_id  INT         NOT NULL,
  to_node_id    INT         NOT NULL,
  distance_m    DOUBLE      NOT NULL,
  edge_type     VARCHAR(10) NOT NULL,
  is_accessible BOOLEAN     NOT NULL DEFAULT TRUE,
  PRIMARY KEY (edge_id),
  CONSTRAINT fk_edge_from FOREIGN KEY (from_node_id) REFERENCES node (node_id),
  CONSTRAINT fk_edge_to   FOREIGN KEY (to_node_id)   REFERENCES node (node_id),
  CONSTRAINT ck_edge_type CHECK (edge_type IN ('WALK', 'STAIRS', 'ELEVATOR')),
  CONSTRAINT ck_edge_distance CHECK (distance_m >= 0),
  CONSTRAINT ck_edge_ends CHECK (from_node_id <> to_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Usage log. One row per route search (event_type ROUTE) or per feedback button press (FEEDBACK).
-- Per D4, feedback has a type and a review status. The route being reported is identified
-- by from_node_id + to_node_id + route_pref on that same row.
-- client_log_id is an addition for offline use (D5): the phone gives every row a random id,
-- so a row sent to the server twice is stored only once.
CREATE TABLE usage_log (
  log_id        BIGINT      NOT NULL AUTO_INCREMENT,
  client_log_id CHAR(36)    NULL,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  event_type    VARCHAR(10) NOT NULL,
  from_node_id  INT         NOT NULL,
  to_node_id    INT         NOT NULL,
  route_pref    VARCHAR(10) NOT NULL DEFAULT 'ANY',
  feedback_type VARCHAR(15) NULL,
  status        VARCHAR(10) NULL,
  note          VARCHAR(255),
  PRIMARY KEY (log_id),
  UNIQUE KEY uq_usage_client_log (client_log_id),
  CONSTRAINT fk_log_from FOREIGN KEY (from_node_id) REFERENCES node (node_id),
  CONSTRAINT fk_log_to   FOREIGN KEY (to_node_id)   REFERENCES node (node_id),
  CONSTRAINT ck_log_event CHECK (event_type IN ('ROUTE', 'FEEDBACK')),
  CONSTRAINT ck_log_pref  CHECK (route_pref IN ('ANY', 'STAIRS', 'ELEVATOR')),
  CONSTRAINT ck_log_feedback CHECK (
    (event_type = 'ROUTE'    AND feedback_type IS NULL AND status IS NULL)
 OR (event_type = 'FEEDBACK' AND feedback_type IN ('HELPFUL', 'WRONG_ROUTE') AND status IN ('NEW', 'REVIEWED'))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
