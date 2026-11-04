package com.uenav.mobile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sends everything waiting in the queue, in batches the server accepts. Never loses an entry on failure. */
public final class SyncService {

    static final int BATCH_SIZE = 200;

    /** @param sent entries the server has taken care of; remaining = entries still waiting */
    public record Outcome(int sent, int remaining, String message) { }

    private final UsageQueue queue;
    private final SyncClient client;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public SyncService(UsageQueue queue, SyncClient client) {
        this.queue = queue;
        this.client = client;
    }

    /** Call from a background thread: it waits for the network. */
    public Outcome syncNow(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return new Outcome(0, queue.size(), "No server address set");
        }
        if (!busy.compareAndSet(false, true)) {
            return new Outcome(0, queue.size(), "Already sending");
        }
        try {
            int sent = 0;
            int rejected = 0;
            while (true) {
                List<UsageEntry> batch = queue.pending(BATCH_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                SyncClient.Result r;
                try {
                    r = client.send(serverUrl, batch);
                } catch (IOException e) {
                    return new Outcome(sent, queue.size(), "Could not send: " + e.getMessage());
                }
                queue.remove(batch);
                sent += batch.size();
                rejected += r.rejected();
            }
            String message = sent == 0 ? "Nothing to send"
                    : "Sent " + sent + (rejected > 0 ? " (" + rejected + " were not valid and were dropped)" : "");
            return new Outcome(sent, queue.size(), message);
        } finally {
            busy.set(false);
        }
    }
}
