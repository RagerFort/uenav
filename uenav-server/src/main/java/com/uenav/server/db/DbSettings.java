package com.uenav.server.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.function.UnaryOperator;

/**
 * Where the built-in database lives and how to log in to it (D24). Every value can be set like the other server
 * settings, as an environment variable or a line in ~/.uenav/server.properties:
 * UENAV_DB_PORT (default 3307, so it never clashes with a MySQL installed on 3306), UENAV_DB_USER (default uenav_app)
 * and UENAV_DB_PASSWORD (made up and saved on the first start when missing). Setting UENAV_DB_URL means "use my own
 * MySQL server instead" and turns the built-in database off.
 */
public record DbSettings(int port, String user, String password, Path home) {

    public static final int DEFAULT_PORT = 3307;
    public static final String DEFAULT_USER = "uenav_app";
    static final String PASSWORD_KEY = "UENAV_DB_PASSWORD";

    /** ~/.uenav: settings, and the database's own folders. */
    public static Path defaultHome() {
        return Path.of(System.getProperty("user.home"), ".uenav");
    }

    /** @param lookup a setting by name, or null/blank when it is not set */
    public static DbSettings from(UnaryOperator<String> lookup, Path home) {
        String port = lookup.apply("UENAV_DB_PORT");
        String user = lookup.apply("UENAV_DB_USER");
        String password = lookup.apply(PASSWORD_KEY);
        return new DbSettings(isBlank(port) ? DEFAULT_PORT : Integer.parseInt(port.trim()),
                isBlank(user) ? DEFAULT_USER : user.trim(), password == null ? "" : password, home);
    }

    /** Settings outside the server (the UENav SQL window): environment variables first, then server.properties. */
    public static DbSettings fromEnvironmentAndFile(Path home) {
        Properties file = readFile(home.resolve("server.properties"));
        return from(key -> {
            String env = System.getenv(key);
            return isBlank(env) ? file.getProperty(key) : env;
        }, home);
    }

    /** True when UENAV_DB_URL points at another MySQL server, so the built-in one is not used. */
    public static boolean usesOwnServer(UnaryOperator<String> lookup) {
        return !isBlank(lookup.apply("UENAV_DB_URL"));
    }

    public String jdbcUrl(String database) {
        return "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
    }

    /** The MariaDB programs, unpacked from the server once. */
    public Path baseDir() {
        return home.resolve("mariadb-" + EmbeddedMariaDb.VERSION);
    }

    /** The data itself: keep this folder (copy it while UENav Server is closed to back it up). */
    public Path dataDir() {
        return home.resolve("database");
    }

    public Path tmpDir() {
        return home.resolve("database-tmp");
    }

    /**
     * The same settings with a password: the saved one, or a new random one written to server.properties (the
     * first start of a fresh install).
     */
    public DbSettings withPassword() throws IOException {
        if (!password.isEmpty()) {
            return this;
        }
        String made = randomPassword();
        Path file = home.resolve("server.properties");
        Files.createDirectories(home);
        boolean newFile = !Files.exists(file);
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.ISO_8859_1, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            if (newFile) {
                w.write("# UENav Server settings\n");
            }
            w.write("\n# password of the built-in database (user " + user + "), made on the first start\n");
            w.write(PASSWORD_KEY + "=" + made + "\n");
        }
        return new DbSettings(port, user, made, home);
    }

    static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static Properties readFile(Path file) {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException unreadable) {
                // no settings: defaults
            }
        }
        return p;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public String toString() {
        return "DbSettings[port=" + port + ", user=" + user + ", home=" + home + "]"; // never the password
    }
}
