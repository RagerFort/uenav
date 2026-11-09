package com.uenav.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingsTest {

    private final MemoryStore store = new MemoryStore();

    @Test
    void startsWithNoServer() {
        assertEquals("", new Settings(store).serverUrl());
    }

    @Test
    void addressIsTidiedAndRemembered() {
        Settings s = new Settings(store);
        s.setServerUrl("  http://192.168.1.10:8080/  ");
        assertEquals("http://192.168.1.10:8080", s.serverUrl());
        assertEquals("http://192.168.1.10:8080", new Settings(store).serverUrl());
        s.setServerUrl("https://uenav.example.edu.ph");
        assertEquals("https://uenav.example.edu.ph", s.serverUrl());
    }

    @Test
    void emptyTextTurnsSendingOff() {
        Settings s = new Settings(store);
        s.setServerUrl("http://10.0.0.5:8080");
        s.setServerUrl("");
        assertEquals("", s.serverUrl());
        assertEquals("", new Settings(store).serverUrl());
    }

    @Test
    void notAnAddressIsRefusedAndTheOldOneIsKept() {
        Settings s = new Settings(store);
        s.setServerUrl("http://10.0.0.5:8080");
        assertThrows(IllegalArgumentException.class, () -> s.setServerUrl("banana"));
        assertThrows(IllegalArgumentException.class, () -> s.setServerUrl("ftp://10.0.0.5"));
        assertThrows(IllegalArgumentException.class, () -> s.setServerUrl("http://"));
        assertThrows(IllegalArgumentException.class, () -> s.setServerUrl("http://a b"));
        assertEquals("http://10.0.0.5:8080", s.serverUrl());
    }

    @Test
    void damagedSettingsFileMeansNoServer() {
        store.text = "][";
        assertEquals("", new Settings(store).serverUrl());
        store.text = "{\"serverUrl\":\"javascript:alert(1)\"}";
        assertEquals("", new Settings(store).serverUrl());
    }

    @Test
    void builtInAddressIsUsedUntilOneIsTyped() {
        Settings s = new Settings(store, "https://uenav-demo.ngrok-free.app/");
        assertEquals("https://uenav-demo.ngrok-free.app", s.serverUrl());
        assertTrue(s.usingBuiltIn());
        s.setServerUrl("http://192.168.1.10:8080");
        assertEquals("http://192.168.1.10:8080", s.serverUrl());
        assertFalse(s.usingBuiltIn());
        assertEquals("http://192.168.1.10:8080", new Settings(store, "https://uenav-demo.ngrok-free.app").serverUrl());
        s.setServerUrl("");
        assertEquals("https://uenav-demo.ngrok-free.app", s.serverUrl());
        assertTrue(s.usingBuiltIn());
    }

    @Test
    void darkModeAndAvoidStairsAreRememberedWithTheAddress() {
        Settings s = new Settings(store);
        assertFalse(s.darkMode());
        assertFalse(s.avoidStairs());
        s.setServerUrl("http://10.0.0.5:8080");
        s.setDarkMode(true);
        s.setAvoidStairs(true);
        Settings again = new Settings(store);
        assertTrue(again.darkMode());
        assertTrue(again.avoidStairs());
        assertEquals("http://10.0.0.5:8080", again.serverUrl());
        again.setServerUrl("");
        assertTrue(new Settings(store).darkMode());
    }

    @Test
    void aChoiceThatCannotBeSavedStillApplies() {
        Settings s = new Settings(store);
        store.failSaves = true;
        s.setDarkMode(true);
        assertTrue(s.darkMode());
    }

    @Test
    void aBadBuiltInAddressIsIgnored() {
        assertEquals("", new Settings(store, "not an address").serverUrl());
        assertEquals("", new Settings(store, null).serverUrl());
    }

    @Test
    void builtInFileIsEmptyOrAValidAddress() {
        String url = BuiltInServer.url();
        assertTrue(url.isEmpty() || url.startsWith("http"), url);
    }
}
