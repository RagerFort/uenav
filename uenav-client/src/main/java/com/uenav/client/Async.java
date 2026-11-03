package com.uenav.client;

import java.util.function.Consumer;
import javafx.application.Platform;

/** Runs a slow server call off the screen thread, then reports back on the screen thread. */
final class Async {

    interface Work<T> {
        T run() throws ApiException;
    }

    private Async() { }

    static <T> void run(Work<T> work, Consumer<T> ok, Consumer<ApiException> fail) {
        Thread t = new Thread(() -> {
            try {
                T result = work.run();
                Platform.runLater(() -> ok.accept(result));
            } catch (ApiException e) {
                Platform.runLater(() -> fail.accept(e));
            } catch (RuntimeException e) {
                ApiException wrapped = new ApiException(0, "Unexpected error: " + e.getMessage());
                Platform.runLater(() -> fail.accept(wrapped));
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
