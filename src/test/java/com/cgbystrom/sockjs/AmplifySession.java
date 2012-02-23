package com.cgbystrom.sockjs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmplifySession implements SessionCallback {
    private static final Logger logger = LoggerFactory.getLogger(AmplifySession.class);

    private Session session;

    @Override
    public void onOpen(Session session) {
        logger.debug("Connected!");
        this.session = session;
    }

    @Override
    public void onClose() {
        logger.debug("Disconnected!");
    }

    @Override
    public void onMessage(String message) {
        int n = Integer.valueOf(message);
        if (n < 0 || n > 19)
            n = 1;

        logger.debug("Received: 2^" + n);
        int z = ((int) Math.pow(2, n));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < z; i++) {
            sb.append('x');
        }
        session.send(sb.toString());
    }

    @Override
    public boolean onError(Throwable exception) {
        logger.error("Error", exception);
        return true;
    }
}