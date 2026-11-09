package com.uenav.mobile;

import com.uenav.core.MiniJson;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * App settings kept on the phone: the server address used for admin sign-in, map updates and sending the usage log.
 * The app can carry a built-in address (uenav-server.json), so it connects without anyone typing one. An address
 * typed in the Admin tab replaces the built-in one; clearing it goes back to the built-in one.
 */
public final class Settings {

    private static final Pattern URL = Pattern.compile("^https?://[^\\s/:]+(:\\d{1,5})?(/\\S*)?$", Pattern.CASE_INSENSITIVE);

    private final TextStore store;
    private final String builtIn;
    private String serverUrl = "";
    private boolean darkMode;
    private boolean avoidStairs;

    public Settings(TextStore store) {
        this(store, "");
    }

    /** @param builtIn the address built into the app, or "" for none (an invalid one is ignored) */
    public Settings(TextStore store, String builtIn) {
        this.store = store;
        this.builtIn = tidy(builtIn);
        load();
    }

    /** The server address to use (typed one, else built-in), or "" if there is none. Never ends with a slash. */
    public synchronized String serverUrl() {
        return serverUrl.isEmpty() ? builtIn : serverUrl;
    }

    /** True when no address was typed and the built-in one is used. */
    public synchronized boolean usingBuiltIn() {
        return serverUrl.isEmpty() && !builtIn.isEmpty();
    }

    /** The address typed in the Admin tab, or "" if none (then the built-in one is used). */
    public synchronized String typedUrl() {
        return serverUrl;
    }

    /** The address built into the app, or "". */
    public String builtInUrl() {
        return builtIn;
    }

    /** A valid address without spaces or a trailing slash, or "" if the text is not an address. */
    static String tidy(String text) {
        String t = text == null ? "" : text.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return URL.matcher(t).matches() ? t : "";
    }

    /**
     * Saves the server address. An empty text goes back to the built-in address (or turns sending off if the app
     * has none).
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
        save();
    }

    /** Dark look for the whole app (also the map). */
    public synchronized boolean darkMode() {
        return darkMode;
    }

    public synchronized void setDarkMode(boolean on) {
        darkMode = on;
        saveQuietly();
    }

    /** Accessibility: routes avoid stairs and use only accessible walkways, elevators and ramps. */
    public synchronized boolean avoidStairs() {
        return avoidStairs;
    }

    public synchronized void setAvoidStairs(boolean on) {
        avoidStairs = on;
        saveQuietly();
    }

    private void save() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("serverUrl", serverUrl);
        root.put("darkMode", darkMode);
        root.put("avoidStairs", avoidStairs);
        store.save(MiniJson.write(root));
    }

    /** A look or route choice that cannot be saved still applies until the app closes. */
    private void saveQuietly() {
        try {
            save();
        } catch (RuntimeException diskProblem) {
            // keep the choice for this session only
        }
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
                Map<String, Object> m = (Map<String, Object>) root;
                Object v = m.get("serverUrl");
                if (v instanceof String s && (s.isEmpty() || URL.matcher(s).matches())) {
                    serverUrl = s;
                }
                darkMode = Boolean.TRUE.equals(m.get("darkMode"));
                avoidStairs = Boolean.TRUE.equals(m.get("avoidStairs"));
            }
        } catch (IllegalArgumentException damagedFile) {
            serverUrl = "";
        }
    }
}
