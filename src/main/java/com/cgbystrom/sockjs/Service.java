package com.cgbystrom.sockjs;

import com.cgbystrom.sockjs.transports.*;
import com.codahale.metrics.MetricRegistry;
import org.jboss.netty.util.Timer;

import java.util.concurrent.ConcurrentHashMap;

import static com.cgbystrom.sockjs.SessionHandler.NotFoundException;

public class Service {
    private String url;
    private SessionCallbackFactory factory;
    private ConcurrentHashMap<String, SessionHandler> sessions = new ConcurrentHashMap<String, SessionHandler>();
    private boolean isWebSocketEnabled = true;
    private int maxResponseSize = 128 * 1024;
    private boolean cookieNeeded = false;
    private Timer timer;
    /** Timeout for when to kill sessions that have not received a connection */
    private int sessionTimeout = 5; // seconds
    private int heartbeatInterval = 25 * 1000; // milliseconds
    private MetricRegistry metricRegistry;
    private Metrics metrics;

    public Service(String url, SessionCallbackFactory factory) {
        this.url = url;
        this.factory = factory;
    }

    public Service(String url, final SessionCallback session) {
        this(url, new SessionCallbackFactory() {
            @Override
            public SessionCallback getSession(String id) throws Exception {
                return session;
            }
        });
    }

    public String getUrl() {
        return url;
    }

    public boolean isWebSocketEnabled() {
        return isWebSocketEnabled;
    }

    public Service setWebSocketEnabled(boolean webSocketEnabled) {
        isWebSocketEnabled = webSocketEnabled;
        return this;
    }

    public int getMaxResponseSize() {
        return maxResponseSize;
    }

    public Service setMaxResponseSize(int maxResponseSize) {
        this.maxResponseSize = maxResponseSize;
        return this;
    }

    public boolean isCookieNeeded() {
        return cookieNeeded;
    }

    public Service setCookieNeeded(boolean cookieNeeded) {
        this.cookieNeeded = cookieNeeded;
        return this;
    }

    public Timer getTimer() {
        return timer;
    }

    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(int heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    public void setMetricRegistry(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    public Metrics getMetrics() {
        if (metrics == null) {
            metrics = new Metrics("com.cgbystrom.sockjs.transports", metricRegistry);
        }
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    public synchronized SessionHandler getOrCreateSession(String sessionId, TransportMetrics tm,
                                                           boolean forceCreate) throws Exception {
        SessionHandler s = sessions.get(sessionId);

        if (s != null && !forceCreate) {
            return s;
        }

        SessionCallback callback = factory.getSession(sessionId);
        SessionHandler newSession = new SessionHandler(sessionId, callback, this, tm);
        SessionHandler existingSession = sessions.putIfAbsent(sessionId, newSession);
        return (existingSession == null) ? newSession : existingSession;
    }

    public synchronized SessionHandler getSession(String sessionId) throws NotFoundException {
        SessionHandler s = sessions.get(sessionId);

        if (s == null) {
            throw new NotFoundException(url, sessionId);
        }

        return s;
    }

    public synchronized SessionHandler destroySession(String sessionId) {
        return sessions.remove(sessionId);
    }

    public static class Metrics {
        final TransportMetrics eventSource;
        final TransportMetrics htmlFile;
        final TransportMetrics jsonp;
        final TransportMetrics rawWebSocket;
        final TransportMetrics webSocket;
        final TransportMetrics xhrPolling;
        final TransportMetrics xhrSend;
        final TransportMetrics xhrStreaming;

        public Metrics(String prefix, MetricRegistry metricRegistry) {
            eventSource = new TransportMetrics(prefix, "eventSource", metricRegistry);
            htmlFile = new TransportMetrics(prefix, "htmlFile", metricRegistry);
            jsonp = new TransportMetrics(prefix, "jsonp", metricRegistry);
            rawWebSocket = new TransportMetrics(prefix, "rawWebSocket", metricRegistry);
            webSocket = new TransportMetrics(prefix, "webSocket", metricRegistry);
            xhrPolling = new TransportMetrics(prefix, "xhrPolling", metricRegistry);
            xhrSend = new TransportMetrics(prefix, "xhrSend", metricRegistry);
            xhrStreaming = new TransportMetrics(prefix, "xhrStreaming", metricRegistry);
        }

        public TransportMetrics getEventSource() {
            return eventSource;
        }

        public TransportMetrics getHtmlFile() {
            return htmlFile;
        }

        public TransportMetrics getJsonp() {
            return jsonp;
        }

        public TransportMetrics getRawWebSocket() {
            return rawWebSocket;
        }

        public TransportMetrics getWebSocket() {
            return webSocket;
        }

        public TransportMetrics getXhrPolling() {
            return xhrPolling;
        }

        public TransportMetrics getXhrSend() {
            return xhrSend;
        }

        public TransportMetrics getXhrStreaming() {
            return xhrStreaming;
        }
    }
}
