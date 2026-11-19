package com.uenav.server.db;

import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfiguration;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * The database built into UENav Server (D24): a MariaDB server (MySQL compatible) that the server starts by itself,
 * so nothing has to be installed or started first. It listens only on this computer (127.0.0.1, port 3307 by
 * default), so MySQL Workbench, the mysql command or the "UENav SQL" window can query it while the server runs.
 *
 * The first start unpacks MariaDB to ~/.uenav/mariadb-11.4.5, makes the data folder ~/.uenav/database, creates the
 * database "uenav" with the user uenav_app (root gets the same password), and loads the campus map from the
 * project's sql/ files. Later starts reuse all of that. A database left running (for example when the window was
 * closed hard) is found and used again instead of starting a second one.
 */
public final class EmbeddedMariaDb {

    public static final String VERSION = "11.4.5";
    public static final String DATABASE = "uenav";
    /** The campus map, in loading order (03 only upgrades old databases, so it is not needed here). */
    static final List<String> SCRIPTS = List.of("01_schema.sql", "02_seed_sample.sql", "04_engineering_building.sql",
            "05_campus_layout.sql", "06_tan_yan_kee.sql");
    private static final Pattern USER_NAME = Pattern.compile("[A-Za-z0-9_]{1,32}");
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedMariaDb.class);

    private final DbSettings settings;
    /** The server process when this program started it; null when one was already running. */
    private Mariadb process;
    /** Close it when this program ends, even if it was already running (UENav Server takes it over). */
    private boolean owner;
    private final AtomicBoolean stopped = new AtomicBoolean();

    private EmbeddedMariaDb(DbSettings settings) {
        this.settings = settings;
    }

    /**
     * Starts the database, or finds it already running, and sets it up on the first start.
     * @param settings with the password (see DbSettings.withPassword)
     */
    public static EmbeddedMariaDb start(DbSettings settings) throws IOException, SQLException {
        if (!USER_NAME.matcher(settings.user()).matches()) {
            throw new IllegalArgumentException("UENAV_DB_USER may only have letters, digits and _");
        }
        EmbeddedMariaDb db = new EmbeddedMariaDb(settings);
        if (db.isRunning()) {
            LOG.info("Database already running on 127.0.0.1:{}; using it", settings.port());
        } else {
            if (portInUse(settings.port())) {
                throw new IllegalStateException("Port " + settings.port() + " is used by another program, not "
                        + "UENav's database. Close it, or choose another port: add UENAV_DB_PORT=3308 to "
                        + settings.home().resolve("server.properties"));
            }
            if (!isWindows()) {
                throw new IllegalStateException("The built-in database is for Windows. Elsewhere, set UENAV_DB_URL "
                        + "to your own MySQL server.");
            }
            db.launch();
        }
        db.prepare();
        return db;
    }

    /** True when this program started the database (and so stops it again). */
    public boolean startedHere() {
        return process != null;
    }

    public DbSettings settings() {
        return settings;
    }

    /** The mysql client program that comes with it (used by the UENav SQL window). */
    public Path clientProgram() {
        return settings.baseDir().resolve("bin").resolve("mysql.exe");
    }

    private void launch() throws IOException {
        boolean firstStart = !Files.isDirectory(settings.dataDir().resolve("mysql"));
        LOG.info(firstStart ? "Setting up the built-in database (first start, takes a little while)..."
                : "Starting the built-in database...");
        DBConfigurationBuilder c = DBConfigurationBuilder.newBuilder();
        c.setDatabaseVersion("mariadb-" + VERSION);
        c.setPort(settings.port());
        c.setBaseDir(settings.baseDir().toFile());
        c.setDataDir(settings.dataDir().toFile());
        c.setTmpDir(settings.tmpDir().toString());
        c.setSecurityDisabled(false); // log-ins and passwords on (MariaDB4j's default is off)
        c.setDefaultCharacterSet("utf8mb4");
        c.addArg("--collation-server=utf8mb4_unicode_ci");
        c.addArg("--bind-address=127.0.0.1"); // this computer only: phones talk to the server, never to the database
        try {
            process = new Mariadb(c.build(), settings.baseDir(), settings.dataDir());
            process.prepareAndInstall();
            process.start();
        } catch (ManagedProcessException e) {
            throw new IOException("The built-in database did not start: " + e.getMessage(), e);
        }
    }

    /** Creates the database, the user and the campus map when they are missing. */
    private void prepare() throws SQLException, IOException {
        boolean ready;
        try (Connection app = appLogin()) {
            ready = app != null && hasCampusMap(app);
        }
        if (!ready) {
            try (Connection root = rootLogin()) {
                if (root == null) {
                    throw new SQLException("Cannot log in to the built-in database to set it up. If you changed "
                            + "UENAV_DB_PASSWORD, change it back, or delete " + settings.dataDir() + " to start over.");
                }
                setUpAccounts(root);
            }
            try (Connection app = appLogin()) {
                if (app == null) {
                    throw new SQLException("The database user " + settings.user() + " cannot log in");
                }
                if (!hasCampusMap(app)) {
                    loadCampusMap(app);
                }
            }
        }
        LOG.info("Database ready: MariaDB {} on 127.0.0.1:{}, database {}, user {} (password: {} in {})", VERSION,
                settings.port(), DATABASE, settings.user(), DbSettings.PASSWORD_KEY,
                settings.home().resolve("server.properties"));
    }

    private void setUpAccounts(Connection root) throws SQLException {
        try (Statement st = root.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS " + DATABASE
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            // no log-in without a password: drop anonymous accounts, give root the same password as the app user
            List<String[]> accounts = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT user, host FROM mysql.user")) {
                while (rs.next()) {
                    accounts.add(new String[] {rs.getString(1), rs.getString(2)});
                }
            }
            for (String[] a : accounts) {
                if (a[0].isEmpty()) {
                    exec(root, "DROP USER ?@?", a[0], a[1]);
                }
            }
            for (String host : List.of("localhost", "127.0.0.1", "::1")) {
                exec(root, "CREATE USER IF NOT EXISTS ?@? IDENTIFIED BY ?", settings.user(), host, settings.password());
                exec(root, "ALTER USER ?@? IDENTIFIED BY ?", settings.user(), host, settings.password());
                exec(root, "GRANT ALL PRIVILEGES ON " + DATABASE + ".* TO ?@?", settings.user(), host);
                exec(root, "GRANT SHUTDOWN ON *.* TO ?@?", settings.user(), host); // to close it gently
            }
            for (String[] a : accounts) {
                if ("root".equals(a[0])) {
                    exec(root, "ALTER USER ?@? IDENTIFIED BY ?", a[0], a[1], settings.password());
                }
            }
            st.execute("FLUSH PRIVILEGES");
        }
    }

    private static void loadCampusMap(Connection app) throws SQLException {
        LOG.info("Loading the campus map into the database (first start)...");
        app.setCatalog(DATABASE);
        for (String script : SCRIPTS) {
            ScriptUtils.executeSqlScript(app,
                    new EncodedResource(new ClassPathResource("db/" + script), StandardCharsets.UTF_8));
        }
        try (Statement st = app.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM node")) {
            rs.next();
            LOG.info("Campus map loaded: {} places", rs.getInt(1));
        }
    }

    /** True when the uenav database has its tables. */
    private static boolean hasCampusMap(Connection app) throws SQLException {
        try (PreparedStatement ps = app.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'node'")) {
            ps.setString(1, DATABASE);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static void exec(Connection c, String sql, String... values) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setString(i + 1, values[i]);
            }
            ps.execute();
        }
    }

    /**
     * Makes this program responsible for closing the database, also one left running before (UENav Server does
     * this; the UENav SQL window only closes a database it started itself).
     */
    public void takeOver() {
        owner = true;
    }

    /**
     * Makes sure the database is shut down properly once this program has ended, however it ends. Closing a
     * console window on Windows ends Java at once, before its own shutdown code can run, so a small hidden helper
     * (PowerShell, part of Windows) waits for this program to end and then sends SHUTDOWN with the mysql program.
     * Java starts it without a window and apart from this window, so closing the window does not end it.
     */
    public void closeWhenThisProgramEnds() {
        if (!isWindows()) {
            return;
        }
        String script = "Wait-Process -Id " + ProcessHandle.current().pid() + " -ErrorAction SilentlyContinue; & '"
                + clientProgram().toString().replace("'", "''") + "' --host=127.0.0.1 --port=" + settings.port()
                + " --user=" + settings.user() + " --connect-timeout=5 -e SHUTDOWN";
        String root = System.getenv("SystemRoot");
        String powershell = root == null ? "powershell.exe"
                : Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString();
        ProcessBuilder pb = new ProcessBuilder(powershell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy",
                "Bypass", "-Command", script);
        pb.environment().put("MYSQL_PWD", settings.password()); // not on the command line
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            pb.start();
        } catch (IOException e) {
            LOG.warn("The database may stay open after UENav closes: {}", e.getMessage());
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Closes the database gently, when this program started it or took it over. Safe to call more than once. */
    public void stop() {
        if ((process == null && !owner) || stopped.getAndSet(true)) {
            return;
        }
        LOG.info("Closing the built-in database...");
        try (Connection c = appLogin()) {
            if (c != null) {
                try (Statement st = c.createStatement()) {
                    st.execute("SHUTDOWN");
                }
            }
        } catch (SQLException closing) {
            // the connection drops while it shuts down
        }
        // it stops listening first and then writes everything to disk: wait for the program itself to end
        boolean ended = waitUntilEnded(30_000);
        if (process != null) {
            try {
                process.stop(); // only does something if it is still running
            } catch (ManagedProcessException e) {
                LOG.warn("Could not stop the database: {}", e.getMessage());
            }
        } else if (!ended) {
            LOG.warn("The database is still closing");
        }
    }

    /** Waits for this copy's bin/mysqld.exe to end by itself (after SHUTDOWN); false when it still runs. */
    private boolean waitUntilEnded(long ms) {
        String exe = settings.baseDir().resolve("bin").resolve("mysqld.exe").toAbsolutePath().normalize().toString();
        long until = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < until) {
            boolean running = ProcessHandle.allProcesses().anyMatch(ph -> ph.info().command()
                    .map(c -> c.equalsIgnoreCase(exe)).orElse(false));
            if (!running) {
                return true;
            }
            pause(150);
        }
        return false;
    }

    private Connection appLogin() {
        return login(settings.user(), settings.password());
    }

    /** root with the password, or with none (right after the first install). */
    private Connection rootLogin() {
        Connection c = login("root", settings.password());
        return c != null ? c : login("root", "");
    }

    /** True when UENav's database already answers (as the app user or root). */
    private boolean isRunning() {
        Connection c = appLogin();
        if (c == null) {
            c = rootLogin();
        }
        if (c == null) {
            return false;
        }
        try {
            c.close();
        } catch (SQLException ignored) {
            // only checking
        }
        return true;
    }

    private Connection login(String user, String password) {
        try {
            return DriverManager.getConnection(settings.jdbcUrl("") + "&connectTimeout=3000", user, password);
        } catch (SQLException e) {
            return null;
        }
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static boolean portInUse(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** MariaDB4j's DB, but keeping what is already set up, and stopped by us (gently) rather than killed. */
    private static final class Mariadb extends DB {

        private final Path base;
        private final Path data;

        Mariadb(DBConfiguration c, Path base, Path data) {
            super(c);
            this.base = base;
            this.data = data;
            dbStartMaxWaitInMS = 120_000; // the first start on a slow computer
        }

        void prepareAndInstall() throws ManagedProcessException {
            prepareDirectories();
            unpackEmbeddedDb();
            install();
        }

        @Override
        protected void unpackEmbeddedDb() {
            Path marker = base.resolve(".unpacked");
            if (Files.exists(marker) && Files.exists(base.resolve("bin/mysqld.exe"))) {
                return;
            }
            super.unpackEmbeddedDb();
            try {
                Files.writeString(marker, VERSION);
            } catch (IOException e) {
                // unpacked again next time
            }
        }

        @Override
        protected synchronized void install() throws ManagedProcessException {
            if (!Files.isDirectory(data.resolve("mysql"))) {
                super.install();
            }
        }

        @Override
        protected void cleanupOnExit() {
            // MariaDB4j would kill the server when Java exits; EmbeddedMariaDb.stop() shuts it down properly
        }
    }
}
