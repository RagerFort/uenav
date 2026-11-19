package com.uenav.server;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a PowerShell command once this program has ended, however it ends (D24, D25). Closing a console window on
 * Windows ends Java at once, before its own shutdown code can run, so a small hidden helper waits for this program
 * to end and then does the clean-up (closing the database, stopping ngrok). Java starts the helper without a window
 * and apart from this window, so closing the window does not end the helper.
 */
public final class AfterExit {

    private static final Logger LOG = LoggerFactory.getLogger(AfterExit.class);

    private AfterExit() { }

    /**
     * @param command PowerShell to run afterwards; must not contain double quotes
     * @param env extra environment variables for it (for example a password, kept off the command line)
     * @return false when the helper could not be started (or not on Windows)
     */
    public static boolean run(String command, Map<String, String> env) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return false;
        }
        String script = "Wait-Process -Id " + ProcessHandle.current().pid() + " -ErrorAction SilentlyContinue; "
                + command;
        String root = System.getenv("SystemRoot");
        String powershell = root == null ? "powershell.exe"
                : Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString();
        ProcessBuilder pb = new ProcessBuilder(powershell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy",
                "Bypass", "-Command", script);
        pb.environment().putAll(env);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            pb.start();
            return true;
        } catch (IOException e) {
            LOG.warn("Clean-up helper not started: {}", e.getMessage());
            return false;
        }
    }

    /** A value inside single quotes in PowerShell. */
    public static String quoted(String s) {
        return "'" + s.replace("'", "''") + "'";
    }
}
