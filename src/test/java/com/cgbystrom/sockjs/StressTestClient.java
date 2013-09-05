package com.cgbystrom.sockjs;

import com.cgbystrom.sockjs.client.SockJsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

public class StressTestClient implements SessionCallback {
    private static final Logger logger = LoggerFactory.getLogger(StressTestClient.class);

    private SockJsClient client;

    public StressTestClient(int port) throws URISyntaxException {
        URI url = new URI("http://localhost:" + port + "/stresstest");
        client = SockJsClient.newLocalClient(url, SockJsClient.Protocol.WEBSOCKET, this);
    }

    public void connect() throws Exception {
        client.connect();
    }

    public void disconnect() {
        client.disconnect();
    }

    @Override
    public void onOpen(Session session) throws Exception {
        logger.debug("onOpen");
    }

    @Override
    public void onClose() throws Exception {
        logger.debug("onClose");
    }

    @Override
    public void onMessage(String message) throws Exception {
        logger.debug("onMessage");
    }

    @Override
    public boolean onError(Throwable exception) {
        logger.error("onError", exception);
        return false;
    }
}
