package com.uenav.mobile;

import com.uenav.core.MiniJson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sends entries to POST /api/usage (through Http, which also copes with networks that block the server's name). */
public final class HttpSyncClient implements SyncClient {

    @Override
    public Result send(String baseUrl, List<UsageEntry> entries) throws IOException {
        List<Object> list = new ArrayList<>();
        for (UsageEntry e : entries) {
            list.add(e.toMap());
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("entries", list);
        Http.Reply r = Http.call("POST", baseUrl + "/api/usage", root, null);
        if (r.status() != 200) {
            throw new IOException("the server answered " + r.status());
        }
        return parse(r.text());
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
