package com.cgbystrom.sockjs;

public interface SessionCallback {
    public void onOpen(Session session) throws Exception; // FIXME: Request parameter?
    public void onClose() throws Exception;
    public void onMessage(String message) throws Exception;
    /** If return false, then silence the exception */
    public boolean onError(Throwable exception);
}
