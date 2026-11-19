package com.uenav.server.tunnel;

import com.uenav.server.AfterExit;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * Puts the server online through ngrok when it starts (D25), so phones on any Wi-Fi or mobile data reach it at the
 * address built into the app. Turned on by a line in ~/.uenav/server.properties (or an environment variable):
 * UENAV_NGROK_URL=https://your-name.ngrok-free.dev . ngrok must be installed and logged in once
 * (ngrok config add-authtoken ...); UENAV_NGROK_PATH names ngrok.exe if it is not on the PATH.
 *
 * ngrok is started once the server answers, its log is watched for "started tunnel" or an error, and it is stopped
 * again when the server ends (also when the window is closed: see AfterExit). If ngrok already has the address
 * online (started by hand or by the VS Code task), that one is used.
 */
public final class NgrokTunnel implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(NgrokTunnel.class);
    private static final Pattern FIELD = Pattern.compile("(\\w+)=(\"(?:[^\"\\\\]|\\\\.)*\"|\\S+)");
    /** ngrok's own status page, which lists the addresses it has online. */
    private static final String AGENT_API = "http://127.0.0.1:4040/api/tunnels";

    private volatile boolean stopping;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String url = tidyUrl(env.getProperty("UENAV_NGROK_URL"));
        if (url == null) {
            return;
        }
        int port = env.getProperty("local.server.port", Integer.class, env.getProperty("server.port", Integer.class,
                8080));
        if (alreadyOnline(url)) {
            LOG.info("Online at {} (ngrok was already running)", url);
            return;
        }
        String ngrok = env.getProperty("UENAV_NGROK_PATH", "ngrok");
        Process p;
        try {
            ProcessBuilder pb = new ProcessBuilder(ngrok, "http", String.valueOf(port), "--url=" + url,
                    "--log=stdout", "--log-format=logfmt");
            pb.redirectErrorStream(true);
            p = pb.start();
        } catch (IOException e) {
            LOG.warn("ngrok did not start ({}). Install it from ngrok.com and run 'ngrok config add-authtoken ...' "
                    + "once, or set UENAV_NGROK_PATH to ngrok.exe. Until then phones reach the server on this Wi-Fi "
                    + "only.", e.getMessage());
            return;
        }
        LOG.info("Putting the server online at {} (ngrok)...", url);
        // stopped with the server: on a normal exit here, and by a helper when the window is closed
        SpringApplication.getShutdownHandlers().add(() -> {
            stopping = true;
            p.destroy();
        });
        AfterExit.run("Stop-Process -Id " + p.pid() + " -Force -ErrorAction SilentlyContinue", Map.of());
        Thread reader = new Thread(() -> watch(p, url), "ngrok log");
        reader.setDaemon(true);
        reader.start();
    }

    /** Reports ngrok's own news: online, or what went wrong. */
    private void watch(Process p, String url) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                Map<String, String> f = fields(line);
                String lvl = f.getOrDefault("lvl", "");
                if ("started tunnel".equals(f.get("msg"))) {
                    LOG.info("Online at {}: phones on any Wi-Fi or mobile data can reach this server (check {}/api/"
                            + "health in a browser)", f.getOrDefault("url", url), url);
                } else if (lvl.equals("eror") || lvl.equals("crit") || line.startsWith("ERROR")) {
                    LOG.warn("ngrok: {}", f.isEmpty() ? line : f.getOrDefault("err", f.getOrDefault("msg", line)));
                }
            }
        } catch (IOException ended) {
            // ngrok closed
        }
        try {
            int code = p.waitFor();
            if (!stopping) {
                LOG.warn("ngrok stopped (exit {}): phones reach the server on this Wi-Fi only until it is started "
                        + "again", code);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** "encounter-x.ngrok-free.dev/" -> "https://encounter-x.ngrok-free.dev"; null for none. */
    static String tidyUrl(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String u = s.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (!u.toLowerCase(Locale.ROOT).startsWith("http://") && !u.toLowerCase(Locale.ROOT).startsWith("https://")) {
            u = "https://" + u;
        }
        return u;
    }

    /** The key=value fields of one ngrok log line (logfmt), quotes removed. */
    static Map<String, String> fields(String line) {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        Matcher x = FIELD.matcher(line);
        while (x.find()) {
            String v = x.group(2);
            if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                v = v.substring(1, v.length() - 1).replace("\\\"", "\"");
            }
            m.put(x.group(1), v);
        }
        return m;
    }

    /** True when an ngrok on this computer already has this address online. */
    private static boolean alreadyOnline(String url) {
        try {
            HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(800)).build();
            HttpResponse<String> r = c.send(HttpRequest.newBuilder(URI.create(AGENT_API))
                    .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 && List.of(r.body().split("\"public_url\":\"")).stream().skip(1)
                    .anyMatch(rest -> rest.startsWith(url + "\""));
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
