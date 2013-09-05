package com.cgbystrom.sockjs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class StressTestSession implements SessionCallback {
    private static final Logger logger = LoggerFactory.getLogger(StressTestSession.class);

    /**
     * Ensure we track connected/disconencted sessions
     * This is to emulate behavior in a real app keeping track of sessions.
     * Otherwise we would by accident lose track of the LoadTestSession and the GC will get it.
     */
    private static final ConcurrentHashMap<String, StressTestSession> clients = new ConcurrentHashMap<String, StressTestSession>(100000);

    private Session session;

    /** Try emulating behavior of a real app by hogging some memory */
    private final int[] memoryHogger;

    public StressTestSession() {
        memoryHogger = new int[10000];
        for (int i = 0; i < 10000; i++) {
            memoryHogger[i] = i;
        }
    }

    @Override
    public void onOpen(Session session) {
        logger.debug("Connected!");
        this.session = session;
        assert session.getId() != null;
        clients.put(session.getId(), this);
    }

    @Override
    public void onClose() {
        logger.debug("Disconnected!");
        clients.remove(session.getId());
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
