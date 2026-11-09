package com.uenav.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import com.uenav.core.MiniJson;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Buildings, floors and walkways calls, against a small fake server. */
class AdminApiMapTest {

    private record Reply(int status, String body) { }

    private HttpServer server;
    private final Map<String, Reply> routes = new HashMap<>();
    private volatile String lastMethodAndPath;
    private volatile String lastBody;

    private AdminApi start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastMethodAndPath = ex.getRequestMethod() + " " + ex.getRequestURI().getPath();
            lastBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Reply r = routes.getOrDefault(lastMethodAndPath, new Reply(404, "{\"error\":\"no such route\"}"));
            byte[] bytes = r.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(r.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                ex.getResponseBody().write(bytes);
            }
            ex.close();
        });
        server.start();
        routes.put("POST /api/admin/login", new Reply(200,
                "{\"token\":\"t1\",\"fullName\":\"Campus administrator\",\"role\":\"ADMIN\",\"expiresInSeconds\":28800}"));
        AdminApi api = new AdminApi("http://127.0.0.1:" + server.getAddress().getPort());
        api.login("admin", "pw");
        return api;
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentBody() {
        return (Map<String, Object>) MiniJson.parse(lastBody);
    }

    @Test
    void buildingsAreReadWithCategoryAndOptionalDescription() throws Exception {
        routes.put("GET /api/admin/buildings", new Reply(200,
                "[{\"id\":1,\"name\":\"Engineering\",\"category\":\"Building\",\"description\":null},"
                        + "{\"id\":2,\"name\":\"Gym\",\"category\":\"Facility\",\"description\":\"Sports\"}]"));
        AdminApi api = start();
        List<AdminModels.Building> list = api.buildings();
        assertEquals(2, list.size());
        assertEquals("Building", list.get(0).category());
        assertEquals(null, list.get(0).description());
        assertEquals("Sports", list.get(1).description());
    }

    @Test
    void createBuildingSendsTheFormAndReturnsTheId() throws Exception {
        routes.put("POST /api/admin/buildings", new Reply(201, "{\"id\":12}"));
        AdminApi api = start();
        int id = api.createBuilding(new AdminModels.BuildingForm("Library", "Building", null));
        assertEquals(12, id);
        assertEquals("Library", sentBody().get("name"));
        assertEquals("Building", sentBody().get("category"));
        assertEquals(null, sentBody().get("description"));
    }

    @Test
    void updateAndDeleteBuildingUseTheIdInTheAddress() throws Exception {
        routes.put("PUT /api/admin/buildings/5", new Reply(200, "{\"status\":\"updated\"}"));
        routes.put("DELETE /api/admin/buildings/5", new Reply(200, "{\"status\":\"deleted\"}"));
        AdminApi api = start();
        api.updateBuilding(5, new AdminModels.BuildingForm("Library 2", "Building", "New wing"));
        assertEquals("PUT /api/admin/buildings/5", lastMethodAndPath);
        assertEquals("New wing", sentBody().get("description"));
        api.deleteBuilding(5);
        assertEquals("DELETE /api/admin/buildings/5", lastMethodAndPath);
    }

    @Test
    void floorsAreCreatedUpdatedAndDeleted() throws Exception {
        routes.put("POST /api/admin/floors", new Reply(201, "{\"id\":3}"));
        routes.put("PUT /api/admin/floors/3", new Reply(200, "{\"status\":\"updated\"}"));
        routes.put("DELETE /api/admin/floors/3", new Reply(200, "{\"status\":\"deleted\"}"));
        AdminApi api = start();
        assertEquals(3, api.createFloor(new AdminModels.FloorForm(1, -1, "Basement")));
        assertEquals(1, ((Double) sentBody().get("buildingId")).intValue());
        assertEquals(-1, ((Double) sentBody().get("floorNumber")).intValue());
        assertEquals("Basement", sentBody().get("floorName"));
        api.updateFloor(3, new AdminModels.FloorForm(1, 0, "Ground"));
        api.deleteFloor(3);
        assertEquals("DELETE /api/admin/floors/3", lastMethodAndPath);
    }

    @Test
    void walkwaysAreReadWithTypeDistanceAndAccessibility() throws Exception {
        routes.put("GET /api/admin/edges", new Reply(200,
                "[{\"id\":1,\"fromNodeId\":1,\"toNodeId\":2,\"distanceM\":120.5,\"edgeType\":\"WALK\",\"accessible\":true},"
                        + "{\"id\":7,\"fromNodeId\":6,\"toNodeId\":8,\"distanceM\":25,\"edgeType\":\"STAIRS\",\"accessible\":false}]"));
        AdminApi api = start();
        List<AdminModels.Edge> edges = api.edges();
        assertEquals(2, edges.size());
        assertEquals(120.5, edges.get(0).distanceM());
        assertEquals("STAIRS", edges.get(1).edgeType());
        assertFalse(edges.get(1).accessible());
        assertTrue(edges.get(0).accessible());
    }

    @Test
    void createWalkwaySendsEveryField() throws Exception {
        routes.put("POST /api/admin/edges", new Reply(201, "{\"id\":9}"));
        routes.put("PUT /api/admin/edges/9", new Reply(200, "{\"status\":\"updated\"}"));
        AdminApi api = start();
        assertEquals(9, api.createEdge(new AdminModels.EdgeForm(1, 2, 12.5, "ELEVATOR", true)));
        Map<String, Object> m = sentBody();
        assertEquals(1, ((Double) m.get("fromNodeId")).intValue());
        assertEquals(2, ((Double) m.get("toNodeId")).intValue());
        assertEquals(12.5, m.get("distanceM"));
        assertEquals("ELEVATOR", m.get("edgeType"));
        assertEquals(true, m.get("accessible"));
        api.updateEdge(9, new AdminModels.EdgeForm(1, 2, 14, "WALK", false));
        assertEquals(false, sentBody().get("accessible"));
    }

    @Test
    void serverProblemsComeBackAsReadableMessages() throws Exception {
        routes.put("POST /api/admin/edges", new Reply(400,
                "{\"errors\":[\"A walkway must join two different nodes\",\"distanceM must be a number greater than 0\"]}"));
        routes.put("DELETE /api/admin/floors/4", new Reply(409, "{\"error\":\"still used\"}"));
        AdminApi api = start();
        ApiException bad = assertThrows(ApiException.class,
                () -> api.createEdge(new AdminModels.EdgeForm(1, 1, 0, "WALK", true)));
        assertEquals(400, bad.status());
        assertEquals(2, bad.messages().size());
        ApiException conflict = assertThrows(ApiException.class, () -> api.deleteFloor(4));
        assertEquals(409, conflict.status());
    }
}
