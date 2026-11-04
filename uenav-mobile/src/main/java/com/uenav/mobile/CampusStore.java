package com.uenav.mobile;

import com.uenav.core.CampusData;
import com.uenav.core.MiniJson;
import com.uenav.core.SnapshotReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The campus data on the device (D9): the copy built into the app, replaced by a newer copy downloaded from
 * GET /api/snapshot when the server can be reached. A download is only kept if it reads correctly.
 */
public final class CampusStore {

    static final String BUNDLED = "/campus-snapshot.json";

    /** What a check for a newer map did. updated is null when nothing changed. */
    public record Check(CampusData updated, String message) { }

    private final TextStore downloaded;
    private CampusData current;
    private boolean fromDownload;

    /** @param downloaded where a downloaded copy is kept (a file in app storage) */
    public CampusStore(TextStore downloaded) {
        this.downloaded = downloaded;
    }

    /** Loads the downloaded copy if there is a good one, otherwise the copy inside the app. */
    public synchronized CampusData load() throws IOException {
        String saved = null;
        try {
            saved = downloaded.load();
        } catch (RuntimeException unreadable) {
            saved = null;
        }
        if (saved != null && !saved.isBlank()) {
            try {
                current = SnapshotReader.read(saved);
                fromDownload = true;
                return current;
            } catch (IllegalArgumentException damaged) {
                // use the bundled copy instead
            }
        }
        current = bundled();
        fromDownload = false;
        return current;
    }

    public synchronized CampusData current() {
        return current;
    }

    /** True if the map in use was downloaded from the server rather than built into the app. */
    public synchronized boolean isDownloaded() {
        return fromDownload;
    }

    public static CampusData bundled() throws IOException {
        try (InputStream in = CampusStore.class.getResourceAsStream(BUNDLED)) {
            if (in == null) {
                throw new IOException("campus-snapshot.json is not inside the app");
            }
            return SnapshotReader.read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** Call from a background thread. Asks the server for its map version and downloads the map if it differs. */
    public Check checkForUpdate(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return new Check(null, "No server address set");
        }
        try {
            Http.Reply v = Http.call("GET", serverUrl + "/api/snapshot/version", null, null);
            if (!v.ok()) {
                return new Check(null, "Map check failed: " + Http.errorText(v));
            }
            String version = versionOf(v.text());
            synchronized (this) {
                if (current != null && version != null && version.equals(current.version())) {
                    return new Check(null, "Map is up to date");
                }
            }
            Http.Reply s = Http.call("GET", serverUrl + "/api/snapshot", null, null);
            if (!s.ok()) {
                return new Check(null, "Map download failed: " + Http.errorText(s));
            }
            CampusData fresh;
            try {
                fresh = SnapshotReader.read(s.text());
            } catch (IllegalArgumentException bad) {
                return new Check(null, "The downloaded map could not be read, so the old one is kept");
            }
            try {
                downloaded.save(s.text());
            } catch (RuntimeException diskProblem) {
                // still use it for now
            }
            synchronized (this) {
                current = fresh;
                fromDownload = true;
            }
            return new Check(fresh, "Map updated");
        } catch (IOException e) {
            return new Check(null, "Could not reach the server, using the map on this device");
        }
    }

    @SuppressWarnings("unchecked")
    private static String versionOf(String json) {
        try {
            Object root = MiniJson.parse(json);
            if (root instanceof Map) {
                Object v = ((Map<String, Object>) root).get("version");
                return v instanceof String s ? s : null;
            }
        } catch (IllegalArgumentException ignored) {
            // treat as unknown
        }
        return null;
    }
}
