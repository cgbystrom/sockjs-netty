package com.cgbystrom.sockjs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoSession implements SessionCallback
{
    private static final Logger logger = LoggerFactory.getLogger(EchoSession.class);
    private Session session;

    @Override
    public void onOpen(Session session) {
        logger.debug("Connected!");
        this.session = session;
    }

    @Override
    public void onClose() {
        logger.debug("Disconnected!");
        session = null;
    }

    @Override
    public void onMessage(String message) {
        logger.debug("Echoing back message");
        session.send(message);
    }

    @Override
    public boolean onError(Throwable exception) {
        logger.error("Error", exception);
        return true;
    }
}
