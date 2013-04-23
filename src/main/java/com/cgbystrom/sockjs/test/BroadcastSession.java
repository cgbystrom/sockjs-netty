package com.cgbystrom.sockjs.test;

import com.cgbystrom.sockjs.Session;
import com.cgbystrom.sockjs.SessionCallback;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class BroadcastSession implements SessionCallback {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(BroadcastSession.class);
    private static final Set<Session> sessions = new HashSet<Session>();

    private Session session;
    private String name;

    @Override
    public void onOpen(Session session) {
        logger.debug("Connected!");
        sessions.add(session);
        this.session = session;
    }

    @Override
    public void onClose() {
        logger.debug("Disconnected!");
        sessions.remove(session);
    }

    @Override
    public void onMessage(String message) {
        logger.debug("Broadcasting received message: " + message);
        for (Session s : sessions) {
            s.send(message);
        }
    }

    @Override
    public boolean onError(Throwable exception) {
        logger.error("Error", exception);
        return true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
