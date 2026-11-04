package com.uenav.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
