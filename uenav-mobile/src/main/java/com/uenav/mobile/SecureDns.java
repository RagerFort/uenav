package com.uenav.mobile;

import com.uenav.core.MiniJson;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds a server's address through secure DNS (DNS over HTTPS) when the phone's network will not (D26). Some mobile
 * networks block whole domains in their DNS, ngrok's free ones among them (the owner's carrier answers "does not
 * exist" for *.ngrok-free.dev). Cloudflare (1.1.1.1) and Google (8.8.8.8) are asked by IP address over HTTPS, so
 * the network's DNS is not used at all; their certificates are checked as usual.
 *
 * An address found this way is remembered for a few minutes, so the next calls go straight to it.
 */
final class SecureDns {

    /** Asked in this order: Cloudflare, then Google (both answer the same JSON format). */
    private static final List<String> RESOLVERS = List.of(
            "https://1.1.1.1/dns-query?type=A&name=", "https://8.8.8.8/resolve?type=A&name=");
    /** How long an address found here is used before asking again (ms). */
    static final long REMEMBER_MS = 5 * 60_000;

    private record Found(String ip, long until) { }

    private static final Map<String, Found> FOUND = new ConcurrentHashMap<>();

    private SecureDns() { }

    /** The address found earlier for a host the phone's network would not look up, or null. */
    static String remembered(String host) {
        Found f = FOUND.get(host);
        if (f == null || f.until() < System.currentTimeMillis()) {
            FOUND.remove(host);
            return null;
        }
        return f.ip();
    }

    /** Forgets a host's address (it stopped answering: look it up again next time). */
    static void forget(String host) {
        FOUND.remove(host);
    }

    /**
     * Looks the host up through secure DNS and remembers the address.
     * @throws UnknownHostException when no resolver knows it (or none can be reached)
     */
    static String lookup(String host) throws UnknownHostException {
        IOException last = null;
        for (String resolver : RESOLVERS) {
            try {
                List<String> ips = ask(resolver + URLEncoder.encode(host, StandardCharsets.UTF_8));
                if (!ips.isEmpty()) {
                    FOUND.put(host, new Found(ips.get(0), System.currentTimeMillis() + REMEMBER_MS));
                    return ips.get(0);
                }
            } catch (IOException e) {
                last = e;
            }
        }
        UnknownHostException e = new UnknownHostException(host);
        if (last != null) {
            e.initCause(last);
        }
        throw e;
    }

    private static List<String> ask(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            c.setConnectTimeout(4000);
            c.setReadTimeout(6000);
            c.setRequestProperty("Accept", "application/dns-json");
            c.setRequestProperty("User-Agent", "UENav-app");
            if (c.getResponseCode() != 200) {
                throw new IOException("secure DNS answered " + c.getResponseCode());
            }
            try (InputStream in = c.getInputStream()) {
                return addresses(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally {
            c.disconnect();
        }
    }

    /** The IPv4 addresses (type 1 answers) in a DNS JSON reply; empty when there are none. */
    static List<String> addresses(String json) {
        List<String> ips = new ArrayList<>();
        try {
            if (MiniJson.parse(json) instanceof Map<?, ?> root && root.get("Answer") instanceof List<?> answers) {
                for (Object a : answers) {
                    if (a instanceof Map<?, ?> m && m.get("type") instanceof Number t && t.intValue() == 1
                            && m.get("data") instanceof String ip) {
                        ips.add(ip);
                    }
                }
            }
        } catch (IllegalArgumentException notJson) {
            // no addresses
        }
        return ips;
    }
}
