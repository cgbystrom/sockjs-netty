package com.cgbystrom.sockjs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class BroadcastService implements Service {
    private static final Logger logger = LoggerFactory.getLogger(BroadcastService.class);

    private final Set<Session> sessions = new HashSet<Session>();

    @Override
    public synchronized void onOpen(Session session) {
        logger.debug("Connected!");
        sessions.add(session);
    }

    @Override
    public synchronized void onClose(Session session) {
        logger.debug("Disconnected!");
        sessions.remove(session);
    }

    @Override
    public synchronized void onMessage(Session session, String message) {
        logger.debug("Broadcasting received message: {}", message);
        for (Session s : sessions) {
            s.send(message);
        }
    }

    @Override
    public boolean onError(Session session, Throwable exception) {
        logger.error("Error", exception);
        return true;
    }

    @Override
    public boolean isWebSocketEnabled() {
        return true;
    }
}
