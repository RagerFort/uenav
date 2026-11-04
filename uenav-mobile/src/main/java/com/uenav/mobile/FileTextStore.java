package com.uenav.mobile;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Keeps the text in one file. Writes to a temporary file first so a crash cannot leave half a file. */
public final class FileTextStore implements TextStore {

    private final File file;

    public FileTextStore(File file) {
        this.file = file;
    }

    @Override
    public String load() {
        try {
            return file.isFile() ? Files.readString(file.toPath(), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void save(String text) {
        try {
            File dir = file.getAbsoluteFile().getParentFile();
            if (dir != null) {
                Files.createDirectories(dir.toPath());
            }
            File tmp = new File(dir, file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
