package com.uenav.server.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotServiceTest {

    /** Stand-in for the database. */
    private static class FakeRepo implements SnapshotRepository {
        double edgeDistance = 120;

        public List<Snapshot.BuildingRow> buildings() {
            return List.of(new Snapshot.BuildingRow(1, "Engineering", "Building", null, null, null, null));
        }

        public List<Snapshot.FloorRow> floors() {
            return List.of(new Snapshot.FloorRow(1, 1, 1, "1st floor", null));
        }

        public List<Snapshot.NodeRow> nodes() {
            List<Snapshot.NodeRow> list = new ArrayList<>();
            list.add(new Snapshot.NodeRow(1, null, null, "Gate", "GATE", 0, 0, null, null));
            list.add(new Snapshot.NodeRow(2, 1, 1, "Lobby", "JUNCTION", 10, 0, null, null));
            return list;
        }

        public List<Snapshot.EdgeRow> edges() {
            return List.of(new Snapshot.EdgeRow(1, 1, 2, edgeDistance, "WALK", true));
        }
    }

    @Test
    void snapshotHoldsAllRows() {
        Snapshot s = new SnapshotService(new FakeRepo()).snapshot();
        assertEquals(1, s.buildings().size());
        assertEquals(1, s.floors().size());
        assertEquals(2, s.nodes().size());
        assertEquals(1, s.edges().size());
        assertNull(s.nodes().get(0).buildingId());
    }

    @Test
    void sameDataGivesSameVersion() {
        SnapshotService svc = new SnapshotService(new FakeRepo());
        assertEquals(svc.version(), svc.version());
        assertEquals(16, svc.version().length());
    }

    @Test
    void changedDataGivesNewVersion() {
        FakeRepo repo = new FakeRepo();
        SnapshotService svc = new SnapshotService(repo);
        String before = svc.version();
        repo.edgeDistance = 125;
        assertNotEquals(before, svc.version());
    }
}
