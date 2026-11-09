package com.uenav.server.mapadmin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Checks admin input and writes it to the map tables. All problems are reported together. */
@Service
public class MapAdminService {

    static final Set<String> EDGE_TYPES = Set.of("WALK", "STAIRS", "ELEVATOR");

    /** The input is not acceptable. Becomes HTTP 400. */
    public static class ValidationException extends RuntimeException {
        private final List<String> errors;

        public ValidationException(List<String> errors) {
            super(String.join("; ", errors));
            this.errors = List.copyOf(errors);
        }

        public List<String> errors() {
            return errors;
        }
    }

    /** The record to change or delete does not exist. Becomes HTTP 404. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String what, int id) {
            super(what + " " + id + " was not found");
        }
    }

    private final MapAdminRepository repo;

    public MapAdminService(MapAdminRepository repo) {
        this.repo = repo;
    }

    // ---- buildings
    public int createBuilding(MapInput.BuildingInput in) {
        return repo.insertBuilding(checkBuilding(in));
    }

    public void updateBuilding(int id, MapInput.BuildingInput in) {
        found(repo.updateBuilding(id, checkBuilding(in)), "Building", id);
    }

    public void deleteBuilding(int id) {
        found(repo.deleteBuilding(id), "Building", id);
    }

    // ---- floors
    public int createFloor(MapInput.FloorInput in) {
        return repo.insertFloor(checkFloor(in));
    }

    public void updateFloor(int id, MapInput.FloorInput in) {
        found(repo.updateFloor(id, checkFloor(in)), "Floor", id);
    }

    public void deleteFloor(int id) {
        found(repo.deleteFloor(id), "Floor", id);
    }

    // ---- nodes
    public int createNode(MapInput.NodeInput in) {
        return repo.insertNode(checkNode(in));
    }

    public void updateNode(int id, MapInput.NodeInput in) {
        found(repo.updateNode(id, checkNode(in)), "Node", id);
    }

    public void deleteNode(int id) {
        found(repo.deleteNode(id), "Node", id);
    }

    // ---- edges
    public int createEdge(MapInput.EdgeInput in) {
        return repo.insertEdge(checkEdge(in, null));
    }

    public void updateEdge(int id, MapInput.EdgeInput in) {
        found(repo.updateEdge(id, checkEdge(in, id)), "Edge", id);
    }

    public void deleteEdge(int id) {
        found(repo.deleteEdge(id), "Edge", id);
    }

    // ---- validation
    private MapInput.BuildingInput checkBuilding(MapInput.BuildingInput in) {
        List<String> errors = new ArrayList<>();
        String name = text(in.name(), "name", 100, errors);
        String category = text(in.category(), "category", 50, errors);
        String path = optional(in.imagePath(), "imagePath", 255, errors);
        fail(errors);
        return new MapInput.BuildingInput(name, category, blankToNull(in.description()),
                in.mapX(), in.mapY(), path);
    }

    private MapInput.FloorInput checkFloor(MapInput.FloorInput in) {
        List<String> errors = new ArrayList<>();
        if (in.buildingId() == null) {
            errors.add("buildingId is required");
        } else if (!repo.buildingExists(in.buildingId())) {
            errors.add("buildingId " + in.buildingId() + " does not exist");
        }
        if (in.floorNumber() == null) {
            errors.add("floorNumber is required");
        }
        String name = text(in.floorName(), "floorName", 50, errors);
        String path = optional(in.planImagePath(), "planImagePath", 255, errors);
        fail(errors);
        return new MapInput.FloorInput(in.buildingId(), in.floorNumber(), name, path);
    }

    private MapInput.NodeInput checkNode(MapInput.NodeInput in) {
        List<String> errors = new ArrayList<>();
        String name = text(in.name(), "name", 100, errors);
        String type = text(in.nodeType(), "nodeType", 30, errors);
        if (type != null) {
            type = type.toUpperCase(Locale.ROOT).replace(' ', '_');
        }
        if (in.x() == null) {
            errors.add("x is required");
        }
        if (in.y() == null) {
            errors.add("y is required");
        }
        if (in.buildingId() != null && !repo.buildingExists(in.buildingId())) {
            errors.add("buildingId " + in.buildingId() + " does not exist");
        }
        if (in.floorId() != null) {
            if (in.buildingId() == null) {
                errors.add("floorId needs a buildingId");
            } else {
                Optional<Integer> owner = repo.floorBuildingId(in.floorId());
                if (owner.isEmpty()) {
                    errors.add("floorId " + in.floorId() + " does not exist");
                } else if (!owner.get().equals(in.buildingId())) {
                    errors.add("floorId " + in.floorId() + " does not belong to building " + in.buildingId());
                }
            }
        }
        String description = keepOrClear(in.description(), "description", 500, errors);
        String hours = keepOrClear(in.openHours(), "openHours", 100, errors);
        fail(errors);
        return new MapInput.NodeInput(in.buildingId(), in.floorId(), name, type, in.x(), in.y(), description, hours);
    }

    private MapInput.EdgeInput checkEdge(MapInput.EdgeInput in, Integer editingId) {
        List<String> errors = new ArrayList<>();
        boolean endsOk = true;
        if (in.fromNodeId() == null) {
            errors.add("fromNodeId is required");
            endsOk = false;
        } else if (!repo.nodeExists(in.fromNodeId())) {
            errors.add("fromNodeId " + in.fromNodeId() + " does not exist");
            endsOk = false;
        }
        if (in.toNodeId() == null) {
            errors.add("toNodeId is required");
            endsOk = false;
        } else if (!repo.nodeExists(in.toNodeId())) {
            errors.add("toNodeId " + in.toNodeId() + " does not exist");
            endsOk = false;
        }
        if (endsOk && in.fromNodeId().equals(in.toNodeId())) {
            errors.add("A walkway must join two different nodes");
            endsOk = false;
        }
        if (in.distanceM() == null || in.distanceM().isNaN() || in.distanceM() <= 0) {
            errors.add("distanceM must be a number greater than 0");
        }
        String type = in.edgeType() == null ? null : in.edgeType().trim().toUpperCase(Locale.ROOT);
        if (type == null || !EDGE_TYPES.contains(type)) {
            errors.add("edgeType must be WALK, STAIRS or ELEVATOR");
            type = null;
        }
        if (endsOk && type != null && repo.edgeExists(in.fromNodeId(), in.toNodeId(), type, editingId)) {
            errors.add("A " + type + " walkway between these two nodes already exists");
        }
        fail(errors);
        boolean accessible = in.accessible() == null || in.accessible();
        return new MapInput.EdgeInput(in.fromNodeId(), in.toNodeId(), in.distanceM(), type, accessible);
    }

    private static String text(String value, String field, int max, List<String> errors) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            errors.add(field + " is required");
            return null;
        }
        if (v.length() > max) {
            errors.add(field + " must be at most " + max + " characters");
            return null;
        }
        return v;
    }

    private static String optional(String value, String field, int max, List<String> errors) {
        String v = blankToNull(value);
        if (v != null && v.length() > max) {
            errors.add(field + " must be at most " + max + " characters");
            return null;
        }
        return v;
    }

    /**
     * Optional text that older apps do not send: null means "not sent, keep what is saved" (on update; nothing on
     * insert), blank means "clear it" and becomes "", anything else is trimmed and length-checked.
     */
    private static String keepOrClear(String value, String field, int max, List<String> errors) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.length() > max) {
            errors.add(field + " must be at most " + max + " characters");
            return null;
        }
        return v;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void fail(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static void found(boolean changed, String what, int id) {
        if (!changed) {
            throw new NotFoundException(what, id);
        }
    }
}
