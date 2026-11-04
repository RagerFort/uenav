package com.uenav.mobile;

import com.uenav.core.MiniJson;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** One small HTTP call with plain HttpURLConnection (safe in the Android native build). */
final class Http {

    /** status 0 means the server could not be reached. */
    record Reply(int status, String text) {
        boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    private Http() { }

    static Reply call(String method, String url, Object jsonBody, String token) throws IOException {
        HttpURLConnection c;
        try {
            c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        } catch (IllegalArgumentException e) {
            throw new IOException("Bad server address");
        }
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(5000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/json");
            if (token != null) {
                c.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (jsonBody != null) {
                byte[] body = MiniJson.write(jsonBody).getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body);
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new Reply(code, text);
        } finally {
            c.disconnect();
        }
    }

    /** The server's own error text ("errors" list or "error"), or a plain message with the status. */
    static String errorText(Reply r) {
        try {
            Object parsed = MiniJson.parse(r.text() == null ? "" : r.text());
            if (parsed instanceof Map<?, ?> m) {
                Object errors = m.get("errors");
                if (errors instanceof List<?> list && !list.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (Object e : list) {
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append(e);
                    }
                    return sb.toString();
                }
                if (m.get("error") != null) {
                    return String.valueOf(m.get("error"));
                }
            }
        } catch (IllegalArgumentException notJson) {
            // fall through
        }
        return "The server reported an error (HTTP " + r.status() + ")";
    }
}
