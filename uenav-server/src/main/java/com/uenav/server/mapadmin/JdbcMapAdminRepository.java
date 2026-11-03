package com.uenav.server.mapadmin;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMapAdminRepository implements MapAdminRepository {

    private final JdbcTemplate jdbc;

    public JdbcMapAdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private int insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return an id");
        }
        return key.intValue();
    }

    private boolean update(String sql, Object... args) {
        return jdbc.update(sql, args) > 0;
    }

    private boolean exists(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n != null && n > 0;
    }

    // ---- buildings
    @Override
    public int insertBuilding(MapInput.BuildingInput b) {
        return insert("INSERT INTO building (name, category, description, map_x, map_y, image_path) "
                + "VALUES (?, ?, ?, ?, ?, ?)",
                b.name(), b.category(), b.description(), b.mapX(), b.mapY(), b.imagePath());
    }

    @Override
    public boolean updateBuilding(int id, MapInput.BuildingInput b) {
        return update("UPDATE building SET name=?, category=?, description=?, map_x=?, map_y=?, image_path=? "
                + "WHERE building_id=?",
                b.name(), b.category(), b.description(), b.mapX(), b.mapY(), b.imagePath(), id);
    }

    @Override
    public boolean deleteBuilding(int id) {
        return update("DELETE FROM building WHERE building_id=?", id);
    }

    // ---- floors
    @Override
    public int insertFloor(MapInput.FloorInput f) {
        return insert("INSERT INTO floor (building_id, floor_number, floor_name, plan_image_path) VALUES (?, ?, ?, ?)",
                f.buildingId(), f.floorNumber(), f.floorName(), f.planImagePath());
    }

    @Override
    public boolean updateFloor(int id, MapInput.FloorInput f) {
        return update("UPDATE floor SET building_id=?, floor_number=?, floor_name=?, plan_image_path=? "
                + "WHERE floor_id=?",
                f.buildingId(), f.floorNumber(), f.floorName(), f.planImagePath(), id);
    }

    @Override
    public boolean deleteFloor(int id) {
        return update("DELETE FROM floor WHERE floor_id=?", id);
    }

    // ---- nodes
    @Override
    public int insertNode(MapInput.NodeInput n) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO node (building_id, floor_id, name, node_type, x, y) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            setNullableInt(ps, 1, n.buildingId());
            setNullableInt(ps, 2, n.floorId());
            ps.setString(3, n.name());
            ps.setString(4, n.nodeType());
            ps.setInt(5, n.x());
            ps.setInt(6, n.y());
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return an id");
        }
        return key.intValue();
    }

    @Override
    public boolean updateNode(int id, MapInput.NodeInput n) {
        return jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "UPDATE node SET building_id=?, floor_id=?, name=?, node_type=?, x=?, y=? WHERE node_id=?");
            setNullableInt(ps, 1, n.buildingId());
            setNullableInt(ps, 2, n.floorId());
            ps.setString(3, n.name());
            ps.setString(4, n.nodeType());
            ps.setInt(5, n.x());
            ps.setInt(6, n.y());
            ps.setInt(7, id);
            return ps;
        }) > 0;
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value)
            throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    @Override
    public boolean deleteNode(int id) {
        return update("DELETE FROM node WHERE node_id=?", id);
    }

    // ---- edges
    @Override
    public int insertEdge(MapInput.EdgeInput e) {
        return insert("INSERT INTO edge (from_node_id, to_node_id, distance_m, edge_type, is_accessible) "
                + "VALUES (?, ?, ?, ?, ?)",
                e.fromNodeId(), e.toNodeId(), e.distanceM(), e.edgeType(), e.accessible());
    }

    @Override
    public boolean updateEdge(int id, MapInput.EdgeInput e) {
        return update("UPDATE edge SET from_node_id=?, to_node_id=?, distance_m=?, edge_type=?, is_accessible=? "
                + "WHERE edge_id=?",
                e.fromNodeId(), e.toNodeId(), e.distanceM(), e.edgeType(), e.accessible(), id);
    }

    @Override
    public boolean deleteEdge(int id) {
        return update("DELETE FROM edge WHERE edge_id=?", id);
    }

    // ---- checks
    @Override
    public boolean buildingExists(int id) {
        return exists("SELECT COUNT(*) FROM building WHERE building_id=?", id);
    }

    @Override
    public Optional<Integer> floorBuildingId(int floorId) {
        return jdbc.query("SELECT building_id FROM floor WHERE floor_id=?",
                (rs, i) -> rs.getInt("building_id"), floorId).stream().findFirst();
    }

    @Override
    public boolean nodeExists(int id) {
        return exists("SELECT COUNT(*) FROM node WHERE node_id=?", id);
    }

    @Override
    public boolean edgeExists(int a, int b, String edgeType, Integer excludeEdgeId) {
        String sql = "SELECT COUNT(*) FROM edge WHERE edge_type=? AND "
                + "((from_node_id=? AND to_node_id=?) OR (from_node_id=? AND to_node_id=?))";
        if (excludeEdgeId == null) {
            return exists(sql, edgeType, a, b, b, a);
        }
        return exists(sql + " AND edge_id<>?", edgeType, a, b, b, a, excludeEdgeId);
    }
}
