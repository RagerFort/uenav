package com.uenav.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** The map as the app uses it: a version, the routing graph, the nodes people can pick, buildings and floors. */
public record CampusData(String version, Graph graph, List<Node> places, List<Building> buildings, List<Floor> floors) {

    public CampusData {
        places = List.copyOf(places);
        buildings = List.copyOf(buildings);
        floors = List.copyOf(floors);
    }

    /** For data with no buildings or floors (older snapshots, tests). */
    public CampusData(String version, Graph graph, List<Node> places) {
        this(version, graph, places, List.of(), List.of());
    }

    public Optional<Building> building(Integer id) {
        if (id == null) {
            return Optional.empty();
        }
        return buildings.stream().filter(b -> b.id() == id).findFirst();
    }

    public Optional<Floor> floor(Integer id) {
        if (id == null) {
            return Optional.empty();
        }
        return floors.stream().filter(f -> f.id() == id).findFirst();
    }

    /** Floors of one building, lowest first. */
    public List<Floor> floorsOf(int buildingId) {
        List<Floor> list = new ArrayList<>();
        for (Floor f : floors) {
            if (f.buildingId() == buildingId) {
                list.add(f);
            }
        }
        list.sort(Comparator.comparingInt(Floor::floorNumber));
        return list;
    }
}
