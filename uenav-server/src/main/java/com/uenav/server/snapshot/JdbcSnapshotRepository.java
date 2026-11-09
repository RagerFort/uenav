package com.uenav.server.snapshot;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSnapshotRepository implements SnapshotRepository {

    private final JdbcTemplate jdbc;

    public JdbcSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Snapshot.BuildingRow> buildings() {
        return jdbc.query(
                "SELECT building_id, name, category, description, map_x, map_y, image_path "
                        + "FROM building ORDER BY building_id",
                (rs, i) -> new Snapshot.BuildingRow(
                        rs.getInt("building_id"), rs.getString("name"), rs.getString("category"),
                        rs.getString("description"), rs.getObject("map_x", Integer.class),
                        rs.getObject("map_y", Integer.class), rs.getString("image_path")));
    }

    @Override
    public List<Snapshot.FloorRow> floors() {
        return jdbc.query(
                "SELECT floor_id, building_id, floor_number, floor_name, plan_image_path "
                        + "FROM floor ORDER BY floor_id",
                (rs, i) -> new Snapshot.FloorRow(
                        rs.getInt("floor_id"), rs.getInt("building_id"), rs.getInt("floor_number"),
                        rs.getString("floor_name"), rs.getString("plan_image_path")));
    }

    @Override
    public List<Snapshot.NodeRow> nodes() {
        return jdbc.query(
                "SELECT node_id, building_id, floor_id, name, node_type, x, y, description, open_hours "
                        + "FROM node ORDER BY node_id",
                (rs, i) -> new Snapshot.NodeRow(
                        rs.getInt("node_id"), rs.getObject("building_id", Integer.class),
                        rs.getObject("floor_id", Integer.class), rs.getString("name"),
                        rs.getString("node_type"), rs.getInt("x"), rs.getInt("y"),
                        rs.getString("description"), rs.getString("open_hours")));
    }

    @Override
    public List<Snapshot.EdgeRow> edges() {
        return jdbc.query(
                "SELECT edge_id, from_node_id, to_node_id, distance_m, edge_type, is_accessible "
                        + "FROM edge ORDER BY edge_id",
                (rs, i) -> new Snapshot.EdgeRow(
                        rs.getInt("edge_id"), rs.getInt("from_node_id"), rs.getInt("to_node_id"),
                        rs.getDouble("distance_m"), rs.getString("edge_type"),
                        rs.getBoolean("is_accessible")));
    }
}
