package com.uenav.mobile;

import com.uenav.core.MiniJson;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sends entries to POST /api/usage using plain HttpURLConnection (works inside the Android native build). */
public final class HttpSyncClient implements SyncClient {

    @Override
    public Result send(String baseUrl, List<UsageEntry> entries) throws IOException {
        List<Object> list = new ArrayList<>();
        for (UsageEntry e : entries) {
            list.add(e.toMap());
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("entries", list);
        byte[] body = MiniJson.write(root).getBytes(StandardCharsets.UTF_8);

        HttpURLConnection c;
        try {
            c = (HttpURLConnection) URI.create(baseUrl + "/api/usage").toURL().openConnection();
        } catch (IllegalArgumentException e) {
            throw new IOException("Bad server address");
        }
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(5000);
            c.setReadTimeout(10000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "UENav-app");
            // free ngrok addresses show a warning page instead of the API unless this header is sent
            c.setRequestProperty("ngrok-skip-browser-warning", "true");
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = c.getOutputStream()) {
                out.write(body);
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (code != 200) {
                throw new IOException("the server answered " + code);
            }
            return parse(text);
        } finally {
            c.disconnect();
        }
    }

    @SuppressWarnings("unchecked")
    private static Result parse(String text) throws IOException {
        try {
            Object root = MiniJson.parse(text);
            if (!(root instanceof Map)) {
                throw new IOException("the server sent an unexpected reply");
            }
            Map<String, Object> m = (Map<String, Object>) root;
            Object rejected = m.get("rejected");
            return new Result(count(m.get("accepted")), count(m.get("duplicates")),
                    rejected instanceof List ? ((List<Object>) rejected).size() : 0);
        } catch (IllegalArgumentException e) {
            throw new IOException("the server sent an unexpected reply");
        }
    }

    private static int count(Object v) {
        return v instanceof Double d ? d.intValue() : 0;
    }
}
