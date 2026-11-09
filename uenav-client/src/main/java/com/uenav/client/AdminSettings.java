package com.uenav.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/** Remembers the PC admin app's server address and light/dark look in ~/.uenav/admin.properties. */
final class AdminSettings {

    static final String DEFAULT_SERVER = "http://localhost:8080";

    private AdminSettings() { }

    private static File file() {
        return new File(new File(System.getProperty("user.home", "."), ".uenav"), "admin.properties");
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(file())) {
            p.load(in);
        } catch (IOException notSavedYet) {
            // empty settings
        }
        return p;
    }

    private static void save(String key, String value) {
        Properties p = load();
        p.setProperty(key, value);
        File f = file();
        f.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            p.store(out, "UENav admin app");
        } catch (IOException ignored) {
            // not remembered; the default is used next time
        }
    }

    static String serverUrl() {
        String v = load().getProperty("server", "").trim();
        return v.isEmpty() ? DEFAULT_SERVER : v;
    }

    static void saveServerUrl(String url) {
        save("server", url);
    }

    static boolean darkMode() {
        return "dark".equalsIgnoreCase(load().getProperty("theme", "").trim());
    }

    static void saveDarkMode(boolean dark) {
        save("theme", dark ? "dark" : "light");
    }
}
