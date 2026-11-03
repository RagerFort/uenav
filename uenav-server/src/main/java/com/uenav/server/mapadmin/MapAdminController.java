package com.uenav.server.mapadmin;

import com.uenav.server.snapshot.Snapshot;
import com.uenav.server.snapshot.SnapshotRepository;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin editing of the map. Every address here is behind the admin login (see WebConfig). */
@RestController
@RequestMapping("/api/admin")
public class MapAdminController {

    private final MapAdminService service;
    private final SnapshotRepository reader;

    public MapAdminController(MapAdminService service, SnapshotRepository reader) {
        this.service = service;
        this.reader = reader;
    }

    private static ResponseEntity<Map<String, Object>> created(int id) {
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    private static Map<String, String> ok(String status) {
        return Map.of("status", status);
    }

    // ---- buildings
    @GetMapping("/buildings")
    public List<Snapshot.BuildingRow> buildings() {
        return reader.buildings();
    }

    @PostMapping("/buildings")
    public ResponseEntity<Map<String, Object>> addBuilding(@RequestBody MapInput.BuildingInput in) {
        return created(service.createBuilding(in));
    }

    @PutMapping("/buildings/{id}")
    public Map<String, String> changeBuilding(@PathVariable("id") int id, @RequestBody MapInput.BuildingInput in) {
        service.updateBuilding(id, in);
        return ok("updated");
    }

    @DeleteMapping("/buildings/{id}")
    public Map<String, String> deleteBuilding(@PathVariable("id") int id) {
        service.deleteBuilding(id);
        return ok("deleted");
    }

    // ---- floors
    @GetMapping("/floors")
    public List<Snapshot.FloorRow> floors() {
        return reader.floors();
    }

    @PostMapping("/floors")
    public ResponseEntity<Map<String, Object>> addFloor(@RequestBody MapInput.FloorInput in) {
        return created(service.createFloor(in));
    }

    @PutMapping("/floors/{id}")
    public Map<String, String> changeFloor(@PathVariable("id") int id, @RequestBody MapInput.FloorInput in) {
        service.updateFloor(id, in);
        return ok("updated");
    }

    @DeleteMapping("/floors/{id}")
    public Map<String, String> deleteFloor(@PathVariable("id") int id) {
        service.deleteFloor(id);
        return ok("deleted");
    }

    // ---- nodes
    @GetMapping("/nodes")
    public List<Snapshot.NodeRow> nodes() {
        return reader.nodes();
    }

    @PostMapping("/nodes")
    public ResponseEntity<Map<String, Object>> addNode(@RequestBody MapInput.NodeInput in) {
        return created(service.createNode(in));
    }

    @PutMapping("/nodes/{id}")
    public Map<String, String> changeNode(@PathVariable("id") int id, @RequestBody MapInput.NodeInput in) {
        service.updateNode(id, in);
        return ok("updated");
    }

    @DeleteMapping("/nodes/{id}")
    public Map<String, String> deleteNode(@PathVariable("id") int id) {
        service.deleteNode(id);
        return ok("deleted");
    }

    // ---- edges (walkways)
    @GetMapping("/edges")
    public List<Snapshot.EdgeRow> edges() {
        return reader.edges();
    }

    @PostMapping("/edges")
    public ResponseEntity<Map<String, Object>> addEdge(@RequestBody MapInput.EdgeInput in) {
        return created(service.createEdge(in));
    }

    @PutMapping("/edges/{id}")
    public Map<String, String> changeEdge(@PathVariable("id") int id, @RequestBody MapInput.EdgeInput in) {
        service.updateEdge(id, in);
        return ok("updated");
    }

    @DeleteMapping("/edges/{id}")
    public Map<String, String> deleteEdge(@PathVariable("id") int id) {
        service.deleteEdge(id);
        return ok("deleted");
    }

    // ---- errors
    @ExceptionHandler(MapAdminService.ValidationException.class)
    public ResponseEntity<Map<String, Object>> invalid(MapAdminService.ValidationException e) {
        return ResponseEntity.status(400).body(Map.of("errors", e.errors()));
    }

    @ExceptionHandler(MapAdminService.NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(MapAdminService.NotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }

    /** Foreign keys and unique keys in MySQL: still in use, or a duplicate. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(Map.of("error",
                "This record is still used by other records, or it duplicates an existing one"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> databaseDown(DataAccessException e) {
        return ResponseEntity.status(503).body(Map.of("error", "database unavailable"));
    }
}