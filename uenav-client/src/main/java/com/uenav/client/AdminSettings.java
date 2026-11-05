package com.uenav.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/** Remembers the server address of the PC admin app in ~/.uenav/admin.properties. */
final class AdminSettings {

    static final String DEFAULT_SERVER = "http://localhost:8080";

    private AdminSettings() { }

    private static File file() {
        return new File(new File(System.getProperty("user.home", "."), ".uenav"), "admin.properties");
    }

    static String serverUrl() {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(file())) {
            p.load(in);
        } catch (IOException notSavedYet) {
            return DEFAULT_SERVER;
        }
        String v = p.getProperty("server", "").trim();
        return v.isEmpty() ? DEFAULT_SERVER : v;
    }

    static void saveServerUrl(String url) {
        Properties p = new Properties();
        p.setProperty("server", url);
        File f = file();
        f.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            p.store(out, "UENav admin app");
        } catch (IOException ignored) {
            // not remembered; the default is used next time
        }
    }
}
