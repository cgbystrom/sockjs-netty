package com.cgbystrom.sockjs;

public interface SessionCallbackFactory {
    SessionCallback getSession(String id) throws Exception;
}
