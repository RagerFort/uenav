package com.uenav.server.snapshot;

import java.util.List;

/** Reads the map tables. Every list is ordered by id so the result is always the same order. */
public interface SnapshotRepository {
    List<Snapshot.BuildingRow> buildings();
    List<Snapshot.FloorRow> floors();
    List<Snapshot.NodeRow> nodes();
    List<Snapshot.EdgeRow> edges();
}
