package com.uenav.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uenav.core.RoutePreference;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyncServiceTest {

    private static class FakeClient implements SyncClient {
        final List<Integer> batchSizes = new ArrayList<>();
        int failOnCall = -1;
        int rejectedPerBatch;

        public Result send(String baseUrl, List<UsageEntry> entries) throws IOException {
            batchSizes.add(entries.size());
            if (batchSizes.size() == failOnCall) {
                throw new IOException("no network");
            }
            return new Result(entries.size() - rejectedPerBatch, 0, rejectedPerBatch);
        }
    }

    private final UsageQueue queue = new UsageQueue(new MemoryStore());
    private final FakeClient client = new FakeClient();
    private final SyncService service = new SyncService(queue, client);

    private void fill(int n) {
        for (int i = 0; i < n; i++) {
            queue.addRoute(1, 2, RoutePreference.ANY);
        }
    }

    @Test
    void everythingIsSentAndForgotten() {
        fill(3);
        SyncService.Outcome o = service.syncNow("http://x");
        assertEquals(3, o.sent());
        assertEquals(0, o.remaining());
        assertEquals("Sent 3", o.message());
        assertEquals(0, queue.size());
    }

    @Test
    void largeQueuesGoInBatchesOfTwoHundred() {
        fill(450);
        service.syncNow("http://x");
        assertEquals(List.of(200, 200, 50), client.batchSizes);
        assertEquals(0, queue.size());
    }

    @Test
    void aFailureKeepsEverythingNotYetAccepted() {
        fill(450);
        client.failOnCall = 2;
        SyncService.Outcome o = service.syncNow("http://x");
        assertEquals(200, o.sent());
        assertEquals(250, o.remaining());
        assertTrue(o.message().contains("Could not send"));
        assertTrue(o.message().contains("no network"));
        assertEquals(250, queue.size());
        client.failOnCall = -1;
        assertEquals(0, service.syncNow("http://x").remaining());
    }

    @Test
    void noServerAddressMeansNothingIsSentOrLost() {
        fill(2);
        SyncService.Outcome o = service.syncNow("  ");
        assertEquals(0, o.sent());
        assertEquals(2, o.remaining());
        assertEquals("No server address set", o.message());
        assertEquals(0, client.batchSizes.size());
    }

    @Test
    void emptyQueueSaysSo() {
        assertEquals("Nothing to send", service.syncNow("http://x").message());
    }

    @Test
    void invalidEntriesRejectedByTheServerAreDroppedAndReported() {
        fill(2);
        client.rejectedPerBatch = 1;
        SyncService.Outcome o = service.syncNow("http://x");
        assertEquals(0, o.remaining());
        assertTrue(o.message().contains("1 were not valid"));
    }
}
