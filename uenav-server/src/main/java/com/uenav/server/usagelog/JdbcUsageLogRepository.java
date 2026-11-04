package com.uenav.server.usagelog;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** All times are stored as UTC wall-clock values in DATETIME columns. */
@Repository
public class JdbcUsageLogRepository implements UsageLogRepository {

    private static final String EVENT_SELECT =
            "SELECT u.log_id, u.created_at, u.event_type, u.from_node_id, f.name AS from_name, "
                    + "u.to_node_id, t.name AS to_name, u.route_pref, u.feedback_type, u.status, u.note "
                    + "FROM usage_log u "
                    + "JOIN node f ON f.node_id = u.from_node_id "
                    + "JOIN node t ON t.node_id = u.to_node_id ";

    private static final RowMapper<Usage.Event> EVENT = (rs, i) -> {
        LocalDateTime at = rs.getObject("created_at", LocalDateTime.class);
        return new Usage.Event(rs.getInt("log_id"), at.toInstant(ZoneOffset.UTC).toString(),
                rs.getString("event_type"), rs.getInt("from_node_id"), rs.getString("from_name"),
                rs.getInt("to_node_id"), rs.getString("to_name"), rs.getString("route_pref"),
                rs.getString("feedback_type"), rs.getString("status"), rs.getString("note"));
    };

    private final JdbcTemplate jdbc;

    public JdbcUsageLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean nodeExists(int nodeId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM node WHERE node_id = ?", Integer.class, nodeId);
        return n != null && n > 0;
    }

    @Override
    public boolean insert(Usage.Row r) {
        try {
            jdbc.update("INSERT INTO usage_log (client_log_id, created_at, event_type, from_node_id, to_node_id, "
                            + "route_pref, feedback_type, status, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    r.clientLogId(), r.createdAtUtc(), r.eventType(), r.fromNodeId(), r.toNodeId(),
                    r.routePref(), r.feedbackType(), r.status(), r.note());
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public Usage.Summary summary() {
        return jdbc.queryForObject(
                "SELECT "
                        + "SUM(event_type = 'ROUTE') AS searches, "
                        + "SUM(event_type = 'FEEDBACK' AND feedback_type = 'HELPFUL') AS helpful, "
                        + "SUM(event_type = 'FEEDBACK' AND feedback_type = 'WRONG_ROUTE' AND status = 'NEW') AS open_reports, "
                        + "SUM(event_type = 'FEEDBACK' AND feedback_type = 'WRONG_ROUTE') AS total_reports "
                        + "FROM usage_log",
                (rs, i) -> new Usage.Summary(rs.getLong("searches"), rs.getLong("helpful"),
                        rs.getLong("open_reports"), rs.getLong("total_reports")));
    }

    @Override
    public List<Usage.TopPlace> mostSearched(int limit) {
        return jdbc.query(
                "SELECT n.node_id, n.name, COUNT(*) AS c FROM usage_log u "
                        + "JOIN node n ON n.node_id = u.to_node_id WHERE u.event_type = 'ROUTE' "
                        + "GROUP BY n.node_id, n.name ORDER BY c DESC, n.name LIMIT ?",
                (rs, i) -> new Usage.TopPlace(rs.getInt("node_id"), rs.getString("name"), rs.getLong("c")),
                limit);
    }

    @Override
    public List<Usage.Event> recent(int limit) {
        return jdbc.query(EVENT_SELECT + "ORDER BY u.created_at DESC, u.log_id DESC LIMIT ?", EVENT, limit);
    }

    @Override
    public List<Usage.Event> reports() {
        return jdbc.query(EVENT_SELECT + "WHERE u.event_type = 'FEEDBACK' AND u.feedback_type = 'WRONG_ROUTE' "
                + "ORDER BY u.created_at DESC, u.log_id DESC", EVENT);
    }

    @Override
    public List<Usage.Event> all() {
        return jdbc.query(EVENT_SELECT + "ORDER BY u.created_at, u.log_id", EVENT);
    }

    @Override
    public boolean setReportStatus(int logId, String status) {
        return jdbc.update("UPDATE usage_log SET status = ? WHERE log_id = ? "
                + "AND event_type = 'FEEDBACK' AND feedback_type = 'WRONG_ROUTE'", status, logId) > 0;
    }
}
