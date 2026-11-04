package com.uenav.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** Usage log calls, against a small fake server. */
class AdminApiUsageTest {

    private record Reply(int status, String contentType, String body) { }

    private HttpServer server;
    private final Map<String, Reply> routes = new HashMap<>();
    private volatile String lastMethodAndPath;
    private volatile String lastBody;
    private volatile String lastAccept;

    private AdminApi start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastMethodAndPath = ex.getRequestMethod() + " " + ex.getRequestURI().getPath()
                    + (ex.getRequestURI().getQuery() == null ? "" : "?" + ex.getRequestURI().getQuery());
            lastBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastAccept = ex.getRequestHeaders().getFirst("Accept");
            Reply r = routes.getOrDefault(lastMethodAndPath, new Reply(404, "application/json", "{\"error\":\"no such route\"}"));
            byte[] bytes = r.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", r.contentType());
            ex.sendResponseHeaders(r.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                ex.getResponseBody().write(bytes);
            }
            ex.close();
        });
        server.start();
        routes.put("POST /api/admin/login", new Reply(200, "application/json",
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

    private static Reply json(String body) {
        return new Reply(200, "application/json", body);
    }

    @Test
    void summaryAndMostSearchedAreRead() throws Exception {
        routes.put("GET /api/admin/usage/summary", json("{\"searches\":52,\"helpful\":10,\"openReports\":1,\"totalReports\":3}"));
        routes.put("GET /api/admin/usage/most-searched?limit=5", json(
                "[{\"nodeId\":4,\"name\":\"College of Engineering Building\",\"count\":15},{\"nodeId\":3,\"name\":\"Registrar's Office\",\"count\":12}]"));
        AdminApi api = start();
        AdminModels.UsageSummary s = api.usageSummary();
        assertEquals(52, s.searches());
        assertEquals(1, s.openReports());
        List<AdminModels.TopPlace> top = api.mostSearched(5);
        assertEquals(2, top.size());
        assertEquals("College of Engineering Building", top.get(0).name());
        assertEquals(15, top.get(0).count());
    }

    @Test
    void recentActivityAndReportsKeepOptionalFields() throws Exception {
        String events = "[{\"id\":9,\"createdAt\":\"2026-11-03T08:15:00Z\",\"eventType\":\"FEEDBACK\",\"fromNodeId\":1,"
                + "\"fromName\":\"Hills Gate\",\"toNodeId\":3,\"toName\":\"Gym\",\"routePref\":\"ANY\","
                + "\"feedbackType\":\"WRONG_ROUTE\",\"status\":\"NEW\",\"note\":null},"
                + "{\"id\":8,\"createdAt\":\"2026-11-03T08:10:00Z\",\"eventType\":\"ROUTE\",\"fromNodeId\":1,"
                + "\"fromName\":\"Hills Gate\",\"toNodeId\":3,\"toName\":\"Gym\",\"routePref\":\"ANY\","
                + "\"feedbackType\":null,\"status\":null,\"note\":null}]";
        routes.put("GET /api/admin/usage/recent?limit=8", json(events));
        routes.put("GET /api/admin/reports", json(events));
        AdminApi api = start();
        List<AdminModels.UsageEvent> recent = api.recentActivity(8);
        assertEquals(2, recent.size());
        assertEquals("WRONG_ROUTE", recent.get(0).feedbackType());
        assertEquals(null, recent.get(1).status());
        assertEquals(2, api.reports().size());
    }

    @Test
    void reportStatusIsSentAsJson() throws Exception {
        routes.put("PUT /api/admin/reports/9/status", json("{\"status\":\"updated\"}"));
        AdminApi api = start();
        api.setReportStatus(9, "REVIEWED");
        assertEquals("PUT /api/admin/reports/9/status", lastMethodAndPath);
        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) MiniJson.parse(lastBody);
        assertEquals("REVIEWED", sent.get("status"));
    }

    @Test
    void csvIsReturnedAsPlainTextNotParsed() throws Exception {
        String csv = "\uFEFFlog_id,created_at_utc\r\n1,2026-11-03T08:00:00Z\r\n";
        routes.put("GET /api/admin/usage/export.csv", new Reply(200, "text/csv; charset=UTF-8", csv));
        AdminApi api = start();
        assertEquals(csv, api.exportUsageCsv());
        assertEquals("text/csv", lastAccept);
    }

    @Test
    void csvErrorsStillGiveReadableMessages() throws Exception {
        routes.put("GET /api/admin/usage/export.csv", new Reply(401, "application/json", "{\"error\":\"Login required\"}"));
        AdminApi api = start();
        ApiException e = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, api::exportUsageCsv);
        assertEquals(401, e.status());
        assertTrue(e.getMessage().contains("Login required"));
    }
}
