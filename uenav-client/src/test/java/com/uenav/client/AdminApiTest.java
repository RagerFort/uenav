package com.uenav.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import com.uenav.core.MiniJson;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AdminApiTest {

    private record Reply(int status, String body) { }

    private HttpServer server;
    private final Map<String, Reply> routes = new HashMap<>();
    private volatile String lastAuth;
    private volatile String lastBody;

    private AdminApi start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastAuth = ex.getRequestHeaders().getFirst("Authorization");
            lastBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Reply r = routes.getOrDefault(ex.getRequestMethod() + " " + ex.getRequestURI().getPath(),
                    new Reply(404, "{\"error\":\"no such route\"}"));
            byte[] bytes = r.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(r.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                ex.getResponseBody().write(bytes);
            }
            ex.close();
        });
        server.start();
        return new AdminApi("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static final String LOGIN_OK =
            "{\"token\":\"t1\",\"fullName\":\"Campus administrator\",\"role\":\"ADMIN\",\"expiresInSeconds\":28800}";

    @Test
    void loginSendsCredentialsAndLaterCallsCarryTheToken() throws Exception {
        routes.put("POST /api/admin/login", new Reply(200, LOGIN_OK));
        routes.put("GET /api/admin/buildings", new Reply(200, "[{\"id\":1,\"name\":\"Engineering\",\"category\":\"Building\"}]"));
        AdminApi api = start();

        AdminApi.LoginInfo info = api.login("admin", "pw");
        assertEquals("t1", info.token());
        assertEquals("Campus administrator", info.fullName());
        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) MiniJson.parse(lastBody);
        assertEquals("admin", sent.get("username"));
        assertEquals("pw", sent.get("password"));

        assertEquals("Engineering", api.buildings().get(0).name());
        assertEquals("Bearer t1", lastAuth);
    }

    @Test
    void wrongLoginShowsTheServersMessage() throws Exception {
        routes.put("POST /api/admin/login", new Reply(401, "{\"error\":\"Invalid username or password\"}"));
        AdminApi api = start();
        ApiException e = assertThrows(ApiException.class, () -> api.login("admin", "bad"));
        assertEquals(401, e.status());
        assertEquals("Invalid username or password", e.getMessage());
    }

    @Test
    void nodesAreReadIncludingMissingBuildingAndFloor() throws Exception {
        routes.put("POST /api/admin/login", new Reply(200, LOGIN_OK));
        routes.put("GET /api/admin/nodes", new Reply(200,
                "[{\"id\":1,\"buildingId\":null,\"floorId\":null,\"name\":\"Gate\",\"nodeType\":\"GATE\",\"x\":0,\"y\":-5},"
                + "{\"id\":2,\"buildingId\":1,\"floorId\":2,\"name\":\"Lobby\",\"nodeType\":\"JUNCTION\",\"x\":10,\"y\":20}]"));
        AdminApi api = start();
        api.login("admin", "pw");
        java.util.List<AdminModels.Place> places = api.nodes();
        assertEquals(2, places.size());
        assertEquals(null, places.get(0).buildingId());
        assertEquals(-5, places.get(0).y());
        assertTrue(places.get(0).isSearchable());
        assertEquals(2, places.get(1).floorId());
        assertFalse(places.get(1).isSearchable());
    }

    @Test
    void createNodeSendsTheFormAndReturnsTheNewId() throws Exception {
        routes.put("POST /api/admin/login", new Reply(200, LOGIN_OK));
        routes.put("POST /api/admin/nodes", new Reply(201, "{\"id\":42}"));
        AdminApi api = start();
        api.login("admin", "pw");
        int id = api.createNode(new AdminModels.NodeForm(null, null, "Main Gate", "GATE", 5, 6));
        assertEquals(42, id);
        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) MiniJson.parse(lastBody);
        assertEquals("Main Gate", sent.get("name"));
        assertEquals(5.0, sent.get("x"));
        assertEquals(null, sent.get("buildingId"));
        assertTrue(sent.containsKey("floorId"));
    }

    @Test
    void validationProblemsAreListedOneByOne() throws Exception {
        routes.put("POST /api/admin/login", new Reply(200, LOGIN_OK));
        routes.put("PUT /api/admin/nodes/3", new Reply(400, "{\"errors\":[\"name is required\",\"x is required\"]}"));
        AdminApi api = start();
        api.login("admin", "pw");
        ApiException e = assertThrows(ApiException.class,
                () -> api.updateNode(3, new AdminModels.NodeForm(null, null, "", "GATE", 0, 0)));
        assertEquals(400, e.status());
        assertEquals(2, e.messages().size());
        assertEquals("name is required", e.messages().get(0));
    }

    @Test
    void deleteConflictIsReportedWithItsStatus() throws Exception {
        routes.put("POST /api/admin/login", new Reply(200, LOGIN_OK));
        routes.put("DELETE /api/admin/nodes/7", new Reply(409, "{\"error\":\"This record is still used by other records\"}"));
        routes.put("DELETE /api/admin/nodes/8", new Reply(200, "{\"status\":\"deleted\"}"));
        AdminApi api = start();
        api.login("admin", "pw");
        assertEquals(409, assertThrows(ApiException.class, () -> api.deleteNode(7)).status());
        api.deleteNode(8);
    }

    @Test
    void callsBeforeLoginFailWithoutContactingTheServer() throws Exception {
        AdminApi api = start();
        lastAuth = "untouched";
        ApiException e = assertThrows(ApiException.class, api::nodes);
        assertEquals(401, e.status());
        assertEquals("untouched", lastAuth);
    }

    @Test
    void anUnreachableServerGivesAClearMessage() {
        AdminApi api = new AdminApi("http://127.0.0.1:1");
        ApiException e = assertThrows(ApiException.class, () -> api.login("admin", "pw"));
        assertEquals(0, e.status());
        assertTrue(e.getMessage().contains("Cannot reach the server"));
    }
}
