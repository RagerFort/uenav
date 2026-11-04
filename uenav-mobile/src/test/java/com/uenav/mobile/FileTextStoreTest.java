package com.uenav.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class FileTextStoreTest {

    @Test
    void textRoundTripsAndOverwrites() throws Exception {
        File dir = Files.createTempDirectory("uenav-test").toFile();
        File file = new File(new File(dir, "nested"), "queue.json");
        FileTextStore store = new FileTextStore(file);
        assertEquals(null, store.load());
        store.save("first \u00F1");
        assertEquals("first \u00F1", store.load());
        store.save("second");
        assertEquals("second", new FileTextStore(file).load());
        assertFalse(new File(file.getParentFile(), "queue.json.tmp").exists());
    }

    @Test
    void aQueueSavedToARealFileComesBackAfterReopening() throws Exception {
        File file = new File(Files.createTempDirectory("uenav-test").toFile(), "usage-queue.json");
        UsageQueue q = new UsageQueue(new FileTextStore(file));
        q.addRoute(1, 4, com.uenav.core.RoutePreference.ANY);
        assertEquals(1, new UsageQueue(new FileTextStore(file)).size());
    }
}
