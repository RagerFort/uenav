package com.uenav.mobile;

/** In-memory stand-in for a file, for tests. */
class MemoryStore implements TextStore {
    String text;
    boolean failSaves;

    @Override
    public String load() {
        return text;
    }

    @Override
    public void save(String t) {
        if (failSaves) {
            throw new java.io.UncheckedIOException(new java.io.IOException("disk full"));
        }
        text = t;
    }
}
