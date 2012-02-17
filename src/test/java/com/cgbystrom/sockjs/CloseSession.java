package com.cgbystrom.sockjs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloseSession implements SessionCallback
{
    private static final Logger logger = LoggerFactory.getLogger(ServiceRouter.class);

    @Override
    public void onOpen(Session session) {
        logger.debug("Connected!");
        logger.debug("Closing...");
        session.close();
    }

    @Override
    public void onClose() {
        logger.debug("Disconnected!");
    }

    @Override
    public void onMessage(String message) {
        logger.debug("Received message: {}", message);
    }

    @Override
    public boolean onError(Throwable exception) {
        logger.error("Error", exception);
        return true;
    }
}