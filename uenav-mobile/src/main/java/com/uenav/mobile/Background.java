package com.uenav.mobile;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.application.Platform;

/** Runs slow work (network) off the screen thread and reports back on the screen thread. */
final class Background {

    private Background() { }

    static <T> void run(Callable<T> work, Consumer<T> ok, Consumer<Exception> fail) {
        Thread t = new Thread(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> ok.accept(result));
            } catch (Exception e) {
                Platform.runLater(() -> fail.accept(e));
            }
        }, "uenav-background");
        t.setDaemon(true);
        t.start();
    }
}
