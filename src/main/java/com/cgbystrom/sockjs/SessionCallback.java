package com.cgbystrom.sockjs;

public interface SessionCallback {
    public void onOpen(Session session); // FIXME: Request parameter?
    public void onClose();
    public void onMessage(String message);
    /** If return false, then silence the exception */
    public boolean onError(Throwable exception);
}
