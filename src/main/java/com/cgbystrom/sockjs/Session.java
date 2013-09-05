package com.cgbystrom.sockjs;

public interface Session {
    public void send(String message);
    public void close();
    public String getId();
}
