package com.uenav.client;

import com.uenav.core.CampusData;
import com.uenav.mobile.CampusStore;
import com.uenav.mobile.FileTextStore;
import com.uenav.mobile.HttpSyncClient;
import com.uenav.mobile.Navigator;
import com.uenav.mobile.SyncService;
import com.uenav.mobile.UsageLog;
import com.uenav.mobile.UsageQueue;
import java.io.File;
import javafx.application.Platform;

/**
 * Explore and Map on the PC: the same visitor screens as the phone, in the wide layout. Routes are found on the PC
 * (D5). The map comes from the server's snapshot when it can be reached, otherwise from the last downloaded copy
 * or the copy built into the app. Searches and feedback are saved in ~/.uenav and sent to the server.
 */
final class VisitorSupport {

    private final String server;
    private final CampusStore store;
    private final UsageQueue queue;
    private final SyncService sync;
    private Navigator navigator;
    private String problem;

    VisitorSupport(String server) {
        this.server = server;
        File dir = new File(System.getProperty("user.home", "."), ".uenav");
        dir.mkdirs();
        store = new CampusStore(new FileTextStore(new File(dir, "pc-campus-snapshot.json")));
        queue = new UsageQueue(new FileTextStore(new File(dir, "pc-usage-queue.json")));
        sync = new SyncService(queue, new HttpSyncClient());
    }

    /** The visitor screens, created on first use. Null if no campus map could be loaded (see problem()). */
    Navigator navigator() {
        if (navigator != null) {
            return navigator;
        }
        CampusData data;
        try {
            data = store.load();
        } catch (Exception e) {
            problem = "Could not load the campus map: " + e.getMessage();
            return null;
        }
        navigator = new Navigator(data, UsageLog.of(queue, this::sendInBackground), true);
        Thread t = new Thread(() -> {
            CampusStore.Check c = store.checkForUpdate(server);
            if (c.updated() != null) {
                Platform.runLater(() -> navigator.setData(c.updated()));
            }
            sync.syncNow(server);
        });
        t.setDaemon(true);
        t.start();
        return navigator;
    }

    String problem() {
        return problem;
    }

    private void sendInBackground() {
        Thread t = new Thread(() -> sync.syncNow(server));
        t.setDaemon(true);
        t.start();
    }
}
