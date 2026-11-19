package com.uenav.server.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbSettingsTest {

    @TempDir
    Path home;

    @Test
    void defaultsAvoidAMysqlOnTheUsualPort() {
        DbSettings s = DbSettings.from(k -> null, home);
        assertEquals(3307, s.port());
        assertEquals("uenav_app", s.user());
        assertEquals("", s.password());
        assertEquals(home.resolve("database"), s.dataDir());
        assertTrue(s.jdbcUrl("uenav").startsWith("jdbc:mysql://127.0.0.1:3307/uenav?"));
    }

    @Test
    void settingsAreRead() {
        Map<String, String> m = Map.of("UENAV_DB_PORT", " 3310 ", "UENAV_DB_USER", "campus", "UENAV_DB_PASSWORD", "pw");
        DbSettings s = DbSettings.from(m::get, home);
        assertEquals(3310, s.port());
        assertEquals("campus", s.user());
        assertEquals("pw", s.password());
        assertTrue(DbSettings.usesOwnServer(Map.of("UENAV_DB_URL", "jdbc:mysql://db:3306/uenav")::get));
        assertFalse(DbSettings.usesOwnServer(k -> null));
    }

    @Test
    void aPasswordIsMadeAndSavedOnTheFirstStart() throws Exception {
        DbSettings s = DbSettings.from(k -> null, home).withPassword();
        assertEquals(20, s.password().length());
        String file = Files.readString(home.resolve("server.properties"));
        assertTrue(file.contains("UENAV_DB_PASSWORD=" + s.password()));
        assertEquals(s.password(), DbSettings.fromEnvironmentAndFile(home).password());
        // an existing password is kept, and never shown
        assertEquals(s.password(), s.withPassword().password());
        assertFalse(s.toString().contains(s.password()));
    }
}
