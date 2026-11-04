package com.uenav.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The phone's admin calls, against a small fake server on this computer. */
class MobileAdminApiTest {

    private HttpServer server;
    private final Map<String, String> replies = new ConcurrentHashMap<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private volatile String lastAuth;
    private volatile String lastBody;
    private volatile String lastMethod;

    private String start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String key = ex.getRequestMethod() + " " + ex.getRequestURI().getPath();
            lastMethod = ex.getRequestMethod();
            lastAuth = ex.getRequestHeaders().getFirst("Authorization");
            lastBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String body = replies.getOrDefault(key, "{\"error\":\"not found\"}");
            int status = statuses.getOrDefault(key, replies.containsKey(key) ? 200 : 404);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                ex.getResponseBody().write(bytes);
            }
            ex.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private MobileAdminApi signedIn() throws Exception {
        replies.put("POST /api/admin/login", "{\"token\":\"t1\",\"fullName\":\"Campus administrator\",\"role\":\"ADMIN\"}");
        MobileAdminApi api = new MobileAdminApi(start() + "/");
        api.login("admin", "secret123");
        return api;
    }

    @Test
    void loginKeepsTheTokenAndName() throws Exception {
        MobileAdminApi api = signedIn();
        assertTrue(api.signedIn());
        assertEquals("Campus administrator", api.fullName());
        assertTrue(lastBody.contains("\"username\":\"admin\""));
    }

    @Test
    void wrongPasswordShowsTheServersMessage() throws Exception {
        replies.put("POST /api/admin/login", "{\"error\":\"Wrong username or password\"}");
        statuses.put("POST /api/admin/login", 401);
        MobileAdminApi api = new MobileAdminApi(start());
        MobileAdminApi.Problem p = assertThrows(MobileAdminApi.Problem.class, () -> api.login("admin", "nope"));
        assertEquals(401, p.status());
        assertEquals("Wrong username or password", p.getMessage());
        assertFalse(api.signedIn());
    }

    @Test
    void readsDashboardNumbersWithTheToken() throws Exception {
        MobileAdminApi api = signedIn();
        replies.put("GET /api/admin/usage/summary",
                "{\"searches\":52,\"helpful\":3,\"openReports\":1,\"totalReports\":2}");
        replies.put("GET /api/admin/usage/most-searched", "[{\"nodeId\":4,\"name\":\"Engineering\",\"count\":15}]");
        replies.put("GET /api/admin/usage/recent", "[{\"id\":9,\"createdAt\":\"2026-11-03T08:15:00Z\","
                + "\"eventType\":\"FEEDBACK\",\"fromName\":\"Gate\",\"toName\":\"Gym\",\"routePref\":\"ANY\","
                + "\"feedbackType\":\"WRONG_ROUTE\",\"status\":\"NEW\",\"note\":null}]");
        MobileAdminApi.Summary s = api.summary();
        assertEquals("Bearer t1", lastAuth);
        assertEquals(52, s.searches());
        assertEquals(1, s.openReports());
        assertEquals(List.of(new MobileAdminApi.TopPlace(4, "Engineering", 15)), api.mostSearched(5));
        MobileAdminApi.Event e = api.recent(5).get(0);
        assertEquals("Feedback: wrong route · Gym", AdminScreens.describe(e));
    }

    @Test
    void savesAndDeletesPlaces() throws Exception {
        MobileAdminApi api = signedIn();
        replies.put("POST /api/admin/nodes", "{\"id\":11}");
        replies.put("PUT /api/admin/nodes/11", "");
        replies.put("DELETE /api/admin/nodes/11", "");
        api.savePlace(null, null, null, "Gym", "FACILITY", 5, 6);
        assertEquals("POST", lastMethod);
        assertTrue(lastBody.contains("\"buildingId\":null"));
        assertTrue(lastBody.contains("\"name\":\"Gym\""));
        api.savePlace(11, 1, 2, "Gym", "FACILITY", 5, 6);
        assertEquals("PUT", lastMethod);
        api.deletePlace(11);
        assertEquals("DELETE", lastMethod);
    }

    @Test
    void readsPlacesWithOutdoorNulls() throws Exception {
        MobileAdminApi api = signedIn();
        replies.put("GET /api/admin/nodes", "[{\"id\":1,\"buildingId\":null,\"floorId\":null,\"name\":\"Gate\","
                + "\"nodeType\":\"GATE\",\"x\":0,\"y\":0}]");
        MobileAdminApi.Place p = api.places().get(0);
        assertNull(p.buildingId());
        assertEquals("GATE", p.nodeType());
    }

    @Test
    void anEndedSessionSignsOut() throws Exception {
        MobileAdminApi api = signedIn();
        replies.put("GET /api/admin/usage/summary", "{\"error\":\"Please sign in again\"}");
        statuses.put("GET /api/admin/usage/summary", 401);
        MobileAdminApi.Problem p = assertThrows(MobileAdminApi.Problem.class, api::summary);
        assertEquals(401, p.status());
        assertFalse(api.signedIn());
    }

    @Test
    void noServerGivesAClearMessage() {
        MobileAdminApi api = new MobileAdminApi("http://127.0.0.1:1");
        MobileAdminApi.Problem p = assertThrows(MobileAdminApi.Problem.class, () -> api.login("a", "b"));
        assertEquals(0, p.status());
        assertTrue(p.getMessage().startsWith("Cannot reach the server"));
    }
}
