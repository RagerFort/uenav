package com.uenav.server.db;

import ch.qos.logback.classic.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * The "UENav SQL" window (UENav Server.exe sql): the mysql command line, already logged in to the built-in
 * database, so you can type queries such as  SELECT * FROM building;  (D24). If UENav Server is not running, the
 * database is started for this window and closed again when you leave.
 */
public final class SqlConsole {

    private SqlConsole() { }

    public static void main(String[] args) throws Exception {
        quietLogging();
        DbSettings settings;
        EmbeddedMariaDb db;
        try {
            System.out.println("Connecting to the UENav database...");
            settings = DbSettings.fromEnvironmentAndFile(DbSettings.defaultHome()).withPassword();
            db = EmbeddedMariaDb.start(settings);
        } catch (Exception e) {
            System.out.println("Could not open the database: " + e.getMessage());
            pause();
            return;
        }
        if (db.startedHere()) {
            db.closeWhenThisProgramEnds(); // also when the window is closed with X
        }
        try {
            Path client = db.clientProgram();
            if (!Files.isRegularFile(client)) {
                System.out.println("The mysql program is missing: " + client);
                pause();
                return;
            }
            System.out.println();
            System.out.println("UENav database (MariaDB " + EmbeddedMariaDb.VERSION + ", MySQL compatible), port "
                    + settings.port() + ".");
            System.out.println("Type SQL and end it with ;   for example");
            System.out.println("  SHOW TABLES;");
            System.out.println("  SELECT * FROM building;");
            System.out.println("  SELECT node_id, name, node_type FROM node WHERE name LIKE 'EN 4%';");
            System.out.println("Type exit to leave." + (db.startedHere()
                    ? " (UENav Server is not running: the database closes when you leave.)" : ""));
            System.out.println();
            ProcessBuilder pb = new ProcessBuilder(List.of(client.toString(), "--host=127.0.0.1",
                    "--port=" + settings.port(), "--user=" + settings.user(), "--default-character-set=utf8mb4",
                    "--prompt=uenav> ", EmbeddedMariaDb.DATABASE));
            pb.environment().put("MYSQL_PWD", settings.password()); // not on the command line, where others see it
            pb.inheritIO();
            pb.start().waitFor();
        } finally {
            db.stop();
        }
    }

    private static void quietLogging() {
        if (LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext ctx) {
            ctx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(Level.WARN);
            ctx.getLogger(EmbeddedMariaDb.class).setLevel(Level.INFO);
            ctx.getLogger("ch.vorburger.exec.ManagedProcess").setLevel(Level.OFF);
        }
    }

    private static void pause() {
        System.out.println("Press Enter to close.");
        try {
            System.in.read();
        } catch (java.io.IOException ignored) {
            // closing anyway
        }
    }
}
