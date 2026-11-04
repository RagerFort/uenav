package com.uenav.mobile;

/** Somewhere to keep a small piece of text between app runs. */
public interface TextStore {

    /** @return the saved text, or null if nothing was saved (or it could not be read) */
    String load();

    /** @throws java.io.UncheckedIOException if the text could not be saved */
    void save(String text);
}
