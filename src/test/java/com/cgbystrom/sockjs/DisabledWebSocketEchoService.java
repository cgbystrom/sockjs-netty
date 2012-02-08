package com.cgbystrom.sockjs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisabledWebSocketEchoService implements Service {
    private static final Logger logger = LoggerFactory.getLogger(DisabledWebSocketEchoService.class);
    
    @Override
    public void onOpen(Session session) {
        logger.debug("Connected!");
    }

    @Override
    public void onClose(Session session) {
        logger.debug("Disconnected!");
    }

    @Override
    public void onMessage(Session session, String message) {
        logger.debug("Echoing back message");
        session.send(message);
    }

    @Override
    public boolean onError(Session session, Throwable exception) {
        logger.error("Error", exception);
        return true;
    }

    @Override
    public boolean isWebSocketEnabled() {
        return false;
    }
}
