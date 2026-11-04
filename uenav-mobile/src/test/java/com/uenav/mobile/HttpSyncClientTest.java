package com.uenav.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import com.uenav.core.MiniJson;
import com.uenav.core.RoutePreference;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The real HTTP sending, against a small fake server on this computer. */
class HttpSyncClientTest {

    private HttpServer server;
    private volatile String method;
    private volatile String path;
    private volatile String contentType;
    private volatile String body;
    private volatile int replyStatus = 200;
    private volatile String replyBody = "{\"accepted\":2,\"duplicates\":1,\"rejected\":[{\"index\":0,\"error\":\"x\"}]}";

    private String start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            method = ex.getRequestMethod();
            path = ex.getRequestURI().getPath();
            contentType = ex.getRequestHeaders().getFirst("Content-Type");
            body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = replyBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(replyStatus, bytes.length);
            ex.getResponseBody().write(bytes);
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

    private static List<UsageEntry> entries() {
        UsageQueue q = new UsageQueue(new MemoryStore());
        q.addRoute(1, 4, RoutePreference.ELEVATOR);
        q.addFeedback(1, 4, RoutePreference.ELEVATOR, "WRONG_ROUTE");
        return q.pending(10);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsTheEntriesAsJsonAndReadsTheServersAnswer() throws Exception {
        String base = start();
        SyncClient.Result r = new HttpSyncClient().send(base, entries());
        assertEquals("POST", method);
        assertEquals("/api/usage", path);
        assertTrue(contentType.startsWith("application/json"));
        Map<String, Object> sent = (Map<String, Object>) MiniJson.parse(body);
        List<Object> list = (List<Object>) sent.get("entries");
        assertEquals(2, list.size());
        Map<String, Object> first = (Map<String, Object>) list.get(0);
        assertEquals("ROUTE", first.get("eventType"));
        assertEquals("ELEVATOR", first.get("routePref"));
        assertEquals(4.0, first.get("toNodeId"));
        assertEquals("WRONG_ROUTE", ((Map<String, Object>) list.get(1)).get("feedbackType"));
        assertEquals(2, r.accepted());
        assertEquals(1, r.duplicates());
        assertEquals(1, r.rejected());
    }

    @Test
    void aRefusalFromTheServerIsAnErrorSoNothingIsForgotten() throws Exception {
        String base = start();
        replyStatus = 400;
        replyBody = "{\"error\":\"No entries were sent\"}";
        IOException e = assertThrows(IOException.class, () -> new HttpSyncClient().send(base, entries()));
        assertTrue(e.getMessage().contains("400"));
    }

    @Test
    void anUnreadableAnswerIsAnError() throws Exception {
        String base = start();
        replyBody = "<html>hello</html>";
        assertThrows(IOException.class, () -> new HttpSyncClient().send(base, entries()));
    }

    @Test
    void aServerThatIsNotThereIsAnError() throws Exception {
        String base = start();
        server.stop(0);
        assertThrows(IOException.class, () -> new HttpSyncClient().send(base, entries()));
    }

    @Test
    void aBrokenAddressIsAnError() {
        assertThrows(IOException.class, () -> new HttpSyncClient().send("http://", entries()));
    }
}
