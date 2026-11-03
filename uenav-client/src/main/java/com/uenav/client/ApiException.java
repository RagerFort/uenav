package com.uenav.client;

import java.util.List;

/** A problem talking to the server. status 0 means the server could not be reached. */
public class ApiException extends Exception {

    private final int status;
    private final List<String> messages;

    public ApiException(int status, String message) {
        this(status, List.of(message));
    }

    public ApiException(int status, List<String> messages) {
        super(String.join("\n", messages));
        this.status = status;
        this.messages = List.copyOf(messages);
    }

    public int status() {
        return status;
    }

    public List<String> messages() {
        return messages;
    }
}
