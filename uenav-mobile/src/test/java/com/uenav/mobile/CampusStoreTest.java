package com.uenav.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import com.uenav.core.CampusData;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Downloading a newer map from a small fake server on this computer. */
class CampusStoreTest {

    private static final String NEW_MAP = """
        {"version":"v2","buildings":[],"floors":[],
         "nodes":[{"id":1,"buildingId":null,"floorId":null,"name":"Gate","nodeType":"GATE","x":0,"y":0},
                  {"id":2,"buildingId":null,"floorId":null,"name":"Gym","nodeType":"FACILITY","x":10,"y":0}],
         "edges":[{"id":1,"fromNodeId":1,"toNodeId":2,"distanceM":10,"edgeType":"WALK","accessible":true}]}
        """;

    private HttpServer server;
    private volatile String version = "v2";
    private volatile String snapshot = NEW_MAP;
    private final AtomicInteger snapshotCalls = new AtomicInteger();

    private String start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/snapshot", ex -> {
            String body;
            if (ex.getRequestURI().getPath().endsWith("/version")) {
                body = "{\"version\":\"" + version + "\"}";
            } else {
                snapshotCalls.incrementAndGet();
                body = snapshot;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
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

    @Test
    void usesTheBuiltInMapFirst() throws IOException {
        CampusStore store = new CampusStore(new MemoryStore());
        CampusData d = store.load();
        assertFalse(store.isDownloaded());
        assertFalse(d.places().isEmpty());
        assertFalse(d.buildings().isEmpty());
    }

    @Test
    void downloadsAndKeepsANewerMap() throws IOException {
        MemoryStore file = new MemoryStore();
        CampusStore store = new CampusStore(file);
        store.load();
        CampusStore.Check c = store.checkForUpdate(start());
        assertNotNull(c.updated());
        assertEquals("v2", c.updated().version());
        assertTrue(store.isDownloaded());
        assertNotNull(file.text);

        CampusStore again = new CampusStore(file);
        assertEquals("v2", again.load().version());
        assertTrue(again.isDownloaded());
    }

    @Test
    void doesNotDownloadWhenVersionIsTheSame() throws IOException {
        CampusStore store = new CampusStore(new MemoryStore());
        String url = start();
        version = store.load().version();
        CampusStore.Check c = store.checkForUpdate(url);
        assertNull(c.updated());
        assertEquals("Map is up to date", c.message());
        assertEquals(0, snapshotCalls.get());
    }

    @Test
    void keepsTheOldMapIfTheDownloadIsBroken() throws IOException {
        MemoryStore file = new MemoryStore();
        CampusStore store = new CampusStore(file);
        String before = store.load().version();
        snapshot = "{\"version\":\"v3\",\"nodes\":[";
        CampusStore.Check c = store.checkForUpdate(start());
        assertNull(c.updated());
        assertEquals(before, store.current().version());
        assertNull(file.text);
    }

    @Test
    void aDamagedSavedCopyFallsBackToTheBuiltInMap() throws IOException {
        MemoryStore file = new MemoryStore();
        file.text = "not json";
        CampusStore store = new CampusStore(file);
        store.load();
        assertFalse(store.isDownloaded());
    }

    @Test
    void noServerMeansNoChange() {
        CampusStore store = new CampusStore(new MemoryStore());
        assertNull(store.checkForUpdate("").updated());
        assertNull(store.checkForUpdate("http://127.0.0.1:1").updated());
    }
}
