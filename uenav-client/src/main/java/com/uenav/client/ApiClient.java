package com.uenav.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Minimal HTTP client. No JSON library yet; one gets chosen after the Android spike (T2). */
public class ApiClient {

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Returns "UP" or "DOWN" for the given path, e.g. "/api/health". */
    public String status(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 && res.body().contains("\"UP\"") ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
