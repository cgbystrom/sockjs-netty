package com.cgbystrom.sockjs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.logging.Slf4JLoggerFactory;
import org.junit.Ignore;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple stress test.
 *
 * Connects X number of clients very quickly and then waits Y seconds,
 * then closes all connections.
 *
 * Can be quite useful for detecting memory leaks or profiling overall performance.
 *
 * Uses local channels instead of real sockets to avoid dealing with socket headaches.
 * May not 100% reproduce the scenario seen in the wild card. But it's good enough for most purposes.
 */
@Ignore
public class StressTest {
    public static void main(String[] args) throws Exception {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());

        final int port = 8001;
        final int numClients = 10;

        new StressTestServer(port).start();
        System.out.println("Server running..");

        List<StressTestClient> clients = new ArrayList<StressTestClient>(numClients);
        for (int i = 0; i < numClients; i++) {
            StressTestClient client = new StressTestClient(port);
            client.connect();
            clients.add(client);
        }

        System.out.println("Waiting to disconnect all clients...");
        Thread.sleep(2 * 1000);
        System.out.println("Disconnecting all clients...");

        for (StressTestClient client : clients) {
            client.disconnect();
        }

        System.out.println("All clients disconnected!");
    }
}
