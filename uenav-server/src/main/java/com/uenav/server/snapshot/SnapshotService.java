package com.uenav.server.snapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SnapshotService {

    private final SnapshotRepository repo;

    public SnapshotService(SnapshotRepository repo) {
        this.repo = repo;
    }

    /** Reads the tables and builds the data set. The version changes whenever any map data changes. */
    public Snapshot snapshot() {
        List<Snapshot.BuildingRow> buildings = repo.buildings();
        List<Snapshot.FloorRow> floors = repo.floors();
        List<Snapshot.NodeRow> nodes = repo.nodes();
        List<Snapshot.EdgeRow> edges = repo.edges();
        return new Snapshot(versionOf(buildings, floors, nodes, edges), buildings, floors, nodes, edges);
    }

    public String version() {
        return snapshot().version();
    }

    /** First 16 hex characters of a SHA-256 over every row. Same data always gives the same version. */
    static String versionOf(List<Snapshot.BuildingRow> b, List<Snapshot.FloorRow> f,
                            List<Snapshot.NodeRow> n, List<Snapshot.EdgeRow> e) {
        StringBuilder sb = new StringBuilder();
        b.forEach(r -> sb.append(r).append('\n'));
        f.forEach(r -> sb.append(r).append('\n'));
        n.forEach(r -> sb.append(r).append('\n'));
        e.forEach(r -> sb.append(r).append('\n'));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
