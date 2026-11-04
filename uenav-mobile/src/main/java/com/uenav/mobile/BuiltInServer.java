package com.uenav.mobile;

import com.uenav.core.MiniJson;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The server address built into the app, from /uenav-server.json ({"serverUrl": "https://..."}).
 * Set it before building the APK so every phone connects by itself. Use an address that works from any network,
 * such as an ngrok or cloud address; a PC's Wi-Fi address (192.168.x.x) only works on that same Wi-Fi.
 */
public final class BuiltInServer {

    static final String RESOURCE = "/uenav-server.json";

    private BuiltInServer() { }

    /** The built-in address, or "" if there is none or it is not a valid address. */
    @SuppressWarnings("unchecked")
    public static String url() {
        try (InputStream in = BuiltInServer.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return "";
            }
            Object root = MiniJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            if (root instanceof Map) {
                Object v = ((Map<String, Object>) root).get("serverUrl");
                return v instanceof String s ? Settings.tidy(s) : "";
            }
        } catch (Exception unreadable) {
            // no built-in address
        }
        return "";
    }
}
