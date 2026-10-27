package com.uenav.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VersionTest {
    @Test
    void nameIsStable() {
        assertEquals("UENav 0.1.0", Version.name());
    }
}
