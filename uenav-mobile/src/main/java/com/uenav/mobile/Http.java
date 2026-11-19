package com.uenav.mobile;

import com.uenav.core.MiniJson;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * One small HTTP call with plain HttpURLConnection (safe in the Android native build).
 *
 * When the phone's network cannot look up the server's name (some mobile networks block ngrok's free domains in
 * their DNS), the address is found through secure DNS instead (SecureDns) and the request goes straight to it over
 * a TLS connection that still checks the server's certificate against its real name (D26).
 */
final class Http {

    /** status 0 means the server could not be reached. */
    record Reply(int status, String text) {
        boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    private static final int CONNECT_MS = 5000;
    private static final int READ_MS = 15000;

    private Http() { }

    static Reply call(String method, String url, Object jsonBody, String token) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("no host");
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Bad server address");
        }
        byte[] body = jsonBody == null ? null : MiniJson.write(jsonBody).getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "UENav-app");
        // free ngrok addresses show a warning page instead of the API unless this header is sent
        headers.put("ngrok-skip-browser-warning", "true");
        if (token != null) {
            headers.put("Authorization", "Bearer " + token);
        }
        if (body != null) {
            headers.put("Content-Type", "application/json; charset=UTF-8");
        }
        String host = uri.getHost();
        String ip = SecureDns.remembered(host);
        if (ip == null) {
            try {
                return viaPhone(method, uri, headers, body);
            } catch (UnknownHostException blockedOrOffline) {
                ip = SecureDns.lookup(host); // throws UnknownHostException again when there is no internet
            }
        }
        try {
            return direct(method, uri, ip, headers, body);
        } catch (IOException e) {
            SecureDns.forget(host); // its address may have changed: look it up again next time
            throw e;
        }
    }

    /** The usual way: the phone looks the name up and HttpURLConnection does the rest. */
    private static Reply viaPhone(String method, URI uri, Map<String, String> headers, byte[] body)
            throws IOException {
        HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(CONNECT_MS);
            c.setReadTimeout(READ_MS);
            headers.forEach(c::setRequestProperty);
            if (body != null) {
                c.setDoOutput(true);
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

    /**
     * The same request sent straight to an address found by SecureDns: a TCP connection to that address, TLS with
     * the real name (SNI) and the usual certificate check for that name, then a plain HTTP/1.1 request.
     */
    static Reply direct(String method, URI uri, String ip, Map<String, String> headers, byte[] body)
            throws IOException {
        String host = uri.getHost();
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        int port = uri.getPort() > 0 ? uri.getPort() : https ? 443 : 80;
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(InetAddress.getByAddress(host, InetAddress.getByName(ip).getAddress()),
                port), CONNECT_MS);
        raw.setSoTimeout(READ_MS);
        Socket s = raw;
        if (https) {
            SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                    .createSocket(raw, host, port, true);
            SSLParameters p = ssl.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS"); // the certificate must be for the server's real name
            p.setServerNames(List.of(new SNIHostName(host)));
            ssl.setSSLParameters(p);
            ssl.startHandshake();
            s = ssl;
        }
        try (Socket conn = s) {
            String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            boolean defaultPort = uri.getPort() < 0 || uri.getPort() == (https ? 443 : 80);
            StringBuilder req = new StringBuilder();
            req.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
            req.append("Host: ").append(host).append(defaultPort ? "" : ":" + port).append("\r\n");
            req.append("Connection: close\r\n");
            headers.forEach((k, v) -> req.append(k).append(": ").append(v).append("\r\n"));
            if (body != null) {
                req.append("Content-Length: ").append(body.length).append("\r\n");
            }
            req.append("\r\n");
            OutputStream out = new BufferedOutputStream(conn.getOutputStream());
            out.write(req.toString().getBytes(StandardCharsets.ISO_8859_1));
            if (body != null) {
                out.write(body);
            }
            out.flush();
            return readReply(conn.getInputStream());
        }
    }

    /** Reads an HTTP/1.1 reply: status line, headers, then the body (sized, chunked, or up to the end). */
    static Reply readReply(InputStream raw) throws IOException {
        InputStream in = new BufferedInputStream(raw);
        int code;
        Map<String, String> h;
        do { // skip "100 Continue" and other interim replies
            String status = line(in);
            String[] parts = status.split(" ", 3);
            if (parts.length < 2 || !parts[0].startsWith("HTTP/")) {
                throw new IOException("Not an HTTP reply");
            }
            code = Integer.parseInt(parts[1].trim());
            h = new HashMap<>();
            for (String l = line(in); !l.isEmpty(); l = line(in)) {
                int colon = l.indexOf(':');
                if (colon > 0) {
                    h.put(l.substring(0, colon).trim().toLowerCase(Locale.ROOT), l.substring(colon + 1).trim());
                }
            }
        } while (code >= 100 && code < 200);
        byte[] body;
        if (h.getOrDefault("transfer-encoding", "").toLowerCase(Locale.ROOT).contains("chunked")) {
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            while (true) {
                String size = line(in);
                int semi = size.indexOf(';');
                int n = Integer.parseInt((semi >= 0 ? size.substring(0, semi) : size).trim(), 16);
                if (n == 0) {
                    while (!line(in).isEmpty()) {
                        // trailer lines
                    }
                    break;
                }
                byte[] chunk = in.readNBytes(n);
                if (chunk.length < n) {
                    throw new IOException("The reply ended early");
                }
                all.write(chunk);
                line(in); // the line break after each chunk
            }
            body = all.toByteArray();
        } else if (h.containsKey("content-length")) {
            int n = Integer.parseInt(h.get("content-length"));
            body = in.readNBytes(n);
            if (body.length < n) {
                throw new IOException("The reply ended early");
            }
        } else {
            body = in.readAllBytes();
        }
        return new Reply(code, new String(body, StandardCharsets.UTF_8));
    }

    /** One header line without its line break. */
    private static String line(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            if (b != '\r') {
                sb.append((char) b);
            }
        }
        if (b == -1 && sb.length() == 0) {
            throw new IOException("The reply ended early");
        }
        return sb.toString();
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
