package com.cgbystrom.sockjs;

public class SockJsMessage {
    private String message;

    public SockJsMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "SockJsMessage{" +
                "message='" + message + '\'' +
                '}';
    }
}
