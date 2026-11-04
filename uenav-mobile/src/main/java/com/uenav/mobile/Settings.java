package com.uenav.mobile;

import com.uenav.core.MiniJson;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** App settings kept on the phone. For now only the server address used for sending the usage log. */
public final class Settings {

    private static final Pattern URL = Pattern.compile("^https?://[^\\s/:]+(:\\d{1,5})?(/\\S*)?$", Pattern.CASE_INSENSITIVE);

    private final TextStore store;
    private String serverUrl = "";

    public Settings(TextStore store) {
        this.store = store;
        load();
    }

    /** The server address, or "" if none is set. Never ends with a slash. */
    public synchronized String serverUrl() {
        return serverUrl;
    }

    /**
     * Saves the server address. An empty text turns sending off.
     * @throws IllegalArgumentException if it is not an address like http://192.168.1.10:8080
     */
    public synchronized void setServerUrl(String text) {
        String t = text == null ? "" : text.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        if (!t.isEmpty() && !URL.matcher(t).matches()) {
            throw new IllegalArgumentException("Enter an address like http://192.168.1.10:8080");
        }
        serverUrl = t;
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("serverUrl", serverUrl);
        store.save(MiniJson.write(root));
    }

    @SuppressWarnings("unchecked")
    private void load() {
        String text = store.load();
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            Object root = MiniJson.parse(text);
            if (root instanceof Map) {
                Object v = ((Map<String, Object>) root).get("serverUrl");
                if (v instanceof String s && (s.isEmpty() || URL.matcher(s).matches())) {
                    serverUrl = s;
                }
            }
        } catch (IllegalArgumentException damagedFile) {
            serverUrl = "";
        }
    }
}
