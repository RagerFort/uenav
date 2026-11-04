package com.uenav.mobile;

import com.uenav.core.MiniJson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The admin addresses the phone's Admin tab uses (the same server API as the PC app, D7/D8).
 * Every method blocks, so call it off the screen thread.
 */
public final class MobileAdminApi {

    /** A problem talking to the server. status 401 means the sign-in ended; 0 means no connection. */
    public static final class Problem extends Exception {
        private final int status;

        public Problem(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    public record Summary(long searches, long helpful, long openReports, long totalReports) { }

    public record TopPlace(int nodeId, String name, long count) { }

    public record Event(int id, String createdAt, String eventType, String fromName, String toName,
                        String routePref, String feedbackType, String status) { }

    public record Place(int id, Integer buildingId, Integer floorId, String name, String nodeType, int x, int y) { }

    public record Building(int id, String name) { }

    public record Floor(int id, int buildingId, int floorNumber, String floorName) { }

    private final String baseUrl;
    private String token;
    private String fullName = "";

    public MobileAdminApi(String baseUrl) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    public String fullName() {
        return fullName;
    }

    public boolean signedIn() {
        return token != null;
    }

    public void login(String username, String password) throws Problem {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        Map<String, Object> m = object(send("POST", "/api/admin/login", body, false));
        token = text(m, "token");
        Object name = m.get("fullName");
        fullName = name instanceof String s ? s : username;
    }

    public void logout() {
        try {
            if (token != null) {
                send("POST", "/api/admin/logout", null, true);
            }
        } catch (Problem ignored) {
            // leaving anyway
        }
        token = null;
    }

    public Summary summary() throws Problem {
        Map<String, Object> m = object(send("GET", "/api/admin/usage/summary", null, true));
        return new Summary(number(m, "searches"), number(m, "helpful"), number(m, "openReports"),
                number(m, "totalReports"));
    }

    public List<TopPlace> mostSearched(int limit) throws Problem {
        List<TopPlace> list = new ArrayList<>();
        for (Object o : array(send("GET", "/api/admin/usage/most-searched?limit=" + limit, null, true))) {
            Map<String, Object> m = object(o);
            list.add(new TopPlace((int) number(m, "nodeId"), text(m, "name"), number(m, "count")));
        }
        return list;
    }

    public List<Event> recent(int limit) throws Problem {
        return events("/api/admin/usage/recent?limit=" + limit);
    }

    public List<Event> reports() throws Problem {
        return events("/api/admin/reports");
    }

    public void setReportStatus(int id, String status) throws Problem {
        send("PUT", "/api/admin/reports/" + id + "/status", Map.of("status", status), true);
    }

    public List<Place> places() throws Problem {
        List<Place> list = new ArrayList<>();
        for (Object o : array(send("GET", "/api/admin/nodes", null, true))) {
            Map<String, Object> m = object(o);
            list.add(new Place((int) number(m, "id"), optInt(m, "buildingId"), optInt(m, "floorId"),
                    text(m, "name"), text(m, "nodeType"), (int) number(m, "x"), (int) number(m, "y")));
        }
        return list;
    }

    public List<Building> buildings() throws Problem {
        List<Building> list = new ArrayList<>();
        for (Object o : array(send("GET", "/api/admin/buildings", null, true))) {
            Map<String, Object> m = object(o);
            list.add(new Building((int) number(m, "id"), text(m, "name")));
        }
        return list;
    }

    public List<Floor> floors() throws Problem {
        List<Floor> list = new ArrayList<>();
        for (Object o : array(send("GET", "/api/admin/floors", null, true))) {
            Map<String, Object> m = object(o);
            list.add(new Floor((int) number(m, "id"), (int) number(m, "buildingId"),
                    (int) number(m, "floorNumber"), text(m, "floorName")));
        }
        return list;
    }

    /** Adds a place (id == null) or saves changes to one. */
    public void savePlace(Integer id, Integer buildingId, Integer floorId, String name, String nodeType, int x, int y)
            throws Problem {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("buildingId", buildingId);
        body.put("floorId", floorId);
        body.put("name", name);
        body.put("nodeType", nodeType);
        body.put("x", x);
        body.put("y", y);
        if (id == null) {
            send("POST", "/api/admin/nodes", body, true);
        } else {
            send("PUT", "/api/admin/nodes/" + id, body, true);
        }
    }

    public void deletePlace(int id) throws Problem {
        send("DELETE", "/api/admin/nodes/" + id, null, true);
    }

    private List<Event> events(String path) throws Problem {
        List<Event> list = new ArrayList<>();
        for (Object o : array(send("GET", path, null, true))) {
            Map<String, Object> m = object(o);
            list.add(new Event((int) number(m, "id"), text(m, "createdAt"), text(m, "eventType"),
                    text(m, "fromName"), text(m, "toName"), text(m, "routePref"),
                    optText(m, "feedbackType"), optText(m, "status")));
        }
        return list;
    }

    // ---- HTTP and JSON

    private Object send(String method, String path, Object body, boolean needsLogin) throws Problem {
        if (needsLogin && token == null) {
            throw new Problem(401, "Please sign in first");
        }
        if (baseUrl.isEmpty()) {
            throw new Problem(0, "Set the server address first");
        }
        Http.Reply r;
        try {
            r = Http.call(method, baseUrl + path, body, needsLogin ? token : null);
        } catch (IOException e) {
            throw new Problem(0, "Cannot reach the server at " + baseUrl);
        }
        if (!r.ok()) {
            if (r.status() == 401 && needsLogin) {
                token = null;
            }
            throw new Problem(r.status(), Http.errorText(r));
        }
        if (r.text() == null || r.text().isBlank()) {
            return null;
        }
        try {
            return MiniJson.parse(r.text());
        } catch (IllegalArgumentException e) {
            throw new Problem(r.status(), "The server sent a reply that could not be read");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object o) throws Problem {
        if (!(o instanceof Map)) {
            throw new Problem(200, "Unexpected reply from the server");
        }
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object o) throws Problem {
        if (!(o instanceof List)) {
            throw new Problem(200, "Unexpected reply from the server");
        }
        return (List<Object>) o;
    }

    private static String text(Map<String, Object> m, String key) throws Problem {
        Object v = m.get(key);
        if (!(v instanceof String s)) {
            throw new Problem(200, "Reply is missing " + key);
        }
        return s;
    }

    private static String optText(Map<String, Object> m, String key) {
        return m.get(key) instanceof String s ? s : null;
    }

    private static long number(Map<String, Object> m, String key) throws Problem {
        Object v = m.get(key);
        if (!(v instanceof Double d)) {
            throw new Problem(200, "Reply is missing " + key);
        }
        return d.longValue();
    }

    private static Integer optInt(Map<String, Object> m, String key) {
        return m.get(key) instanceof Double d ? d.intValue() : null;
    }
}
