package com.cgbystrom.sockjs;

public interface Service {
    public void onOpen(Session session); // FIXME: Request parameter?
    public void onClose(Session session);
    public void onMessage(Session session, String message);
    /** If return false, then silence the exception */
    public boolean onError(Session session, Throwable exception);
    public boolean isWebSocketEnabled();
}
