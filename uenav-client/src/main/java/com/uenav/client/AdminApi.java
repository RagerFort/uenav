package com.uenav.client;

import com.uenav.core.MiniJson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Talks to the UENav server's admin addresses. Every method may block, so call it off the screen thread. */
public class AdminApi {

    public record LoginInfo(String token, String fullName, String role) { }

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String token;

    public AdminApi(String baseUrl) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    public LoginInfo login(String username, String password) throws ApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        Map<String, Object> m = asObject(send("POST", "/api/admin/login", body, false));
        token = string(m, "token");
        return new LoginInfo(token, string(m, "fullName"), string(m, "role"));
    }

    public void logout() {
        try {
            if (token != null) {
                send("POST", "/api/admin/logout", null, true);
            }
        } catch (ApiException ignored) {
            // leaving anyway
        }
        token = null;
    }

    public List<AdminModels.Building> buildings() throws ApiException {
        List<AdminModels.Building> list = new ArrayList<>();
        for (Object o : asList(send("GET", "/api/admin/buildings", null, true))) {
            Map<String, Object> m = asObject(o);
            list.add(new AdminModels.Building(integer(m, "id"), string(m, "name"),
                    nullableString(m, "category"), nullableString(m, "description")));
        }
        return list;
    }

    public List<AdminModels.Floor> floors() throws ApiException {
        List<AdminModels.Floor> list = new ArrayList<>();
        for (Object o : asList(send("GET", "/api/admin/floors", null, true))) {
            Map<String, Object> m = asObject(o);
            list.add(new AdminModels.Floor(integer(m, "id"), integer(m, "buildingId"),
                    integer(m, "floorNumber"), string(m, "floorName")));
        }
        return list;
    }

    public List<AdminModels.Place> nodes() throws ApiException {
        List<AdminModels.Place> list = new ArrayList<>();
        for (Object o : asList(send("GET", "/api/admin/nodes", null, true))) {
            Map<String, Object> m = asObject(o);
            list.add(new AdminModels.Place(integer(m, "id"), nullableInteger(m, "buildingId"),
                    nullableInteger(m, "floorId"), string(m, "name"), string(m, "nodeType"),
                    integer(m, "x"), integer(m, "y")));
        }
        return list;
    }

    public int createNode(AdminModels.NodeForm f) throws ApiException {
        return integer(asObject(send("POST", "/api/admin/nodes", nodeBody(f), true)), "id");
    }

    public void updateNode(int id, AdminModels.NodeForm f) throws ApiException {
        send("PUT", "/api/admin/nodes/" + id, nodeBody(f), true);
    }

    public void deleteNode(int id) throws ApiException {
        send("DELETE", "/api/admin/nodes/" + id, null, true);
    }

    // ---- buildings
    public int createBuilding(AdminModels.BuildingForm f) throws ApiException {
        return integer(asObject(send("POST", "/api/admin/buildings", buildingBody(f), true)), "id");
    }

    public void updateBuilding(int id, AdminModels.BuildingForm f) throws ApiException {
        send("PUT", "/api/admin/buildings/" + id, buildingBody(f), true);
    }

    public void deleteBuilding(int id) throws ApiException {
        send("DELETE", "/api/admin/buildings/" + id, null, true);
    }

    private static Map<String, Object> buildingBody(AdminModels.BuildingForm f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", f.name());
        m.put("category", f.category());
        m.put("description", f.description());
        return m;
    }

    // ---- floors
    public int createFloor(AdminModels.FloorForm f) throws ApiException {
        return integer(asObject(send("POST", "/api/admin/floors", floorBody(f), true)), "id");
    }

    public void updateFloor(int id, AdminModels.FloorForm f) throws ApiException {
        send("PUT", "/api/admin/floors/" + id, floorBody(f), true);
    }

    public void deleteFloor(int id) throws ApiException {
        send("DELETE", "/api/admin/floors/" + id, null, true);
    }

    private static Map<String, Object> floorBody(AdminModels.FloorForm f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("buildingId", f.buildingId());
        m.put("floorNumber", f.floorNumber());
        m.put("floorName", f.floorName());
        return m;
    }

    // ---- walkways (edges)
    public List<AdminModels.Edge> edges() throws ApiException {
        List<AdminModels.Edge> list = new ArrayList<>();
        for (Object o : asList(send("GET", "/api/admin/edges", null, true))) {
            Map<String, Object> m = asObject(o);
            list.add(new AdminModels.Edge(integer(m, "id"), integer(m, "fromNodeId"), integer(m, "toNodeId"),
                    decimal(m, "distanceM"), string(m, "edgeType"), bool(m, "accessible")));
        }
        return list;
    }

    public int createEdge(AdminModels.EdgeForm f) throws ApiException {
        return integer(asObject(send("POST", "/api/admin/edges", edgeBody(f), true)), "id");
    }

    public void updateEdge(int id, AdminModels.EdgeForm f) throws ApiException {
        send("PUT", "/api/admin/edges/" + id, edgeBody(f), true);
    }

    public void deleteEdge(int id) throws ApiException {
        send("DELETE", "/api/admin/edges/" + id, null, true);
    }

    private static Map<String, Object> edgeBody(AdminModels.EdgeForm f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fromNodeId", f.fromNodeId());
        m.put("toNodeId", f.toNodeId());
        m.put("distanceM", f.distanceM());
        m.put("edgeType", f.edgeType());
        m.put("accessible", f.accessible());
        return m;
    }

    private static Map<String, Object> nodeBody(AdminModels.NodeForm f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("buildingId", f.buildingId());
        m.put("floorId", f.floorId());
        m.put("name", f.name());
        m.put("nodeType", f.nodeType());
        m.put("x", f.x());
        m.put("y", f.y());
        return m;
    }

    // ---- HTTP
    private Object send(String method, String path, Object body, boolean needsLogin) throws ApiException {
        if (needsLogin && token == null) {
            throw new ApiException(401, "Please sign in first");
        }
        HttpRequest.Builder b;
        try {
            b = HttpRequest.newBuilder(URI.create(baseUrl + path));
        } catch (IllegalArgumentException e) {
            throw new ApiException(0, "The server address is not valid: " + baseUrl);
        }
        b.timeout(Duration.ofSeconds(10)).header("Accept", "application/json");
        if (needsLogin) {
            b.header("Authorization", "Bearer " + token);
        }
        HttpRequest.BodyPublisher publisher;
        if (body == null) {
            publisher = HttpRequest.BodyPublishers.noBody();
        } else {
            b.header("Content-Type", "application/json");
            publisher = HttpRequest.BodyPublishers.ofString(MiniJson.write(body), StandardCharsets.UTF_8);
        }
        b.method(method, publisher);

        HttpResponse<String> res;
        try {
            res = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException(0, "Cannot reach the server at " + baseUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "The request was interrupted");
        }

        int status = res.statusCode();
        String text = res.body();
        if (status >= 200 && status < 300) {
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return MiniJson.parse(text);
            } catch (IllegalArgumentException e) {
                throw new ApiException(status, "The server sent a reply that could not be read");
            }
        }
        throw new ApiException(status, errorMessages(status, text));
    }

    private static List<String> errorMessages(int status, String text) {
        try {
            Object parsed = MiniJson.parse(text == null ? "" : text);
            if (parsed instanceof Map<?, ?> m) {
                Object errors = m.get("errors");
                if (errors instanceof List<?> list && !list.isEmpty()) {
                    List<String> out = new ArrayList<>();
                    for (Object e : list) {
                        out.add(String.valueOf(e));
                    }
                    return out;
                }
                Object error = m.get("error");
                if (error != null) {
                    return List.of(String.valueOf(error));
                }
            }
        } catch (IllegalArgumentException ignored) {
            // not JSON, fall through
        }
        return List.of("The server reported an error (HTTP " + status + ")");
    }

    // ---- reading JSON
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object o) throws ApiException {
        if (!(o instanceof Map)) {
            throw new ApiException(0, "Unexpected reply from the server");
        }
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) throws ApiException {
        if (!(o instanceof List)) {
            throw new ApiException(0, "Unexpected reply from the server");
        }
        return (List<Object>) o;
    }

    private static String string(Map<String, Object> m, String key) throws ApiException {
        Object v = m.get(key);
        if (!(v instanceof String)) {
            throw new ApiException(0, "Unexpected reply from the server (missing " + key + ")");
        }
        return (String) v;
    }

    private static int integer(Map<String, Object> m, String key) throws ApiException {
        Object v = m.get(key);
        if (!(v instanceof Double)) {
            throw new ApiException(0, "Unexpected reply from the server (missing " + key + ")");
        }
        return ((Double) v).intValue();
    }

    private static Integer nullableInteger(Map<String, Object> m, String key) throws ApiException {
        return m.get(key) == null ? null : integer(m, key);
    }

    private static String nullableString(Map<String, Object> m, String key) throws ApiException {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        if (!(v instanceof String)) {
            throw new ApiException(0, "Unexpected reply from the server (bad " + key + ")");
        }
        return (String) v;
    }

    private static double decimal(Map<String, Object> m, String key) throws ApiException {
        Object v = m.get(key);
        if (!(v instanceof Double)) {
            throw new ApiException(0, "Unexpected reply from the server (missing " + key + ")");
        }
        return (Double) v;
    }

    private static boolean bool(Map<String, Object> m, String key) throws ApiException {
        Object v = m.get(key);
        if (!(v instanceof Boolean)) {
            throw new ApiException(0, "Unexpected reply from the server (missing " + key + ")");
        }
        return (Boolean) v;
    }
}