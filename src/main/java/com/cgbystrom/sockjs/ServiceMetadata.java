package com.cgbystrom.sockjs;

import com.cgbystrom.sockjs.transports.*;
import com.codahale.metrics.MetricRegistry;
import org.jboss.netty.util.Timer;

import java.util.concurrent.ConcurrentHashMap;

import static com.cgbystrom.sockjs.SessionHandler.NotFoundException;

public class ServiceMetadata {
    private String url;
    private SessionCallbackFactory factory;
    private ConcurrentHashMap<String, SessionHandler> sessions;
    private boolean isWebSocketEnabled = true;
    private int maxResponseSize;
    private boolean jsessionid = false;
    private Timer timer;
    /** Timeout for when to kill sessions that have not received a connection */
    private int sessionTimeout = 5; // seconds
    private int heartbeatInterval = 30; // seconds
    private Metrics metrics;

    /*public ServiceMetadata(String url, SessionCallbackFactory factory, ConcurrentHashMap<String,
            SessionHandler> sessions, boolean isWebSocketEnabled, int maxResponseSize) {
        this.url = url;
        this.factory = factory;
        this.sessions = sessions;
        this.isWebSocketEnabled = isWebSocketEnabled;
        this.maxResponseSize = maxResponseSize;
    }*/

    // Package level constructor
    ServiceMetadata(Timer timer, MetricRegistry metricRegistry) {
        this.timer = timer;
        metrics = new Metrics(metricRegistry);
    }

    public String getUrl() {
        return url;
    }

    public ServiceMetadata setUrl(String url) {
        this.url = url;
        return this;
    }

    public SessionCallbackFactory getFactory() {
        return factory;
    }

    public ServiceMetadata setFactory(SessionCallbackFactory factory) {
        this.factory = factory;
        return this;
    }

    public ConcurrentHashMap<String, SessionHandler> getSessions() {
        return sessions;
    }

    public ServiceMetadata setSessions(ConcurrentHashMap<String, SessionHandler> sessions) {
        this.sessions = sessions;
        return this;
    }

    public boolean isWebSocketEnabled() {
        return isWebSocketEnabled;
    }

    public ServiceMetadata setWebSocketEnabled(boolean webSocketEnabled) {
        isWebSocketEnabled = webSocketEnabled;
        return this;
    }

    public int getMaxResponseSize() {
        return maxResponseSize;
    }

    public ServiceMetadata setMaxResponseSize(int maxResponseSize) {
        this.maxResponseSize = maxResponseSize;
        return this;
    }

    public boolean isJsessionid() {
        return jsessionid;
    }

    public ServiceMetadata setJsessionid(boolean jsessionid) {
        this.jsessionid = jsessionid;
        return this;
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

    public Timer getTimer() {
        return timer;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public static class Metrics {
        TransportMetrics total;
        final TransportMetrics eventSource;
        final TransportMetrics htmlFile;
        final TransportMetrics jsonp;
        final TransportMetrics rawWebSocket;
        final TransportMetrics webSocket;
        final TransportMetrics xhrPolling;
        final TransportMetrics xhrSend;
        final TransportMetrics xhrStreaming;

        public Metrics(MetricRegistry metricRegistry) {
            eventSource = new TransportMetrics("eventSource", metricRegistry);
            htmlFile = new TransportMetrics("htmlFile", metricRegistry);
            jsonp = new TransportMetrics("jsonp", metricRegistry);
            rawWebSocket = new TransportMetrics("rawWebSocket", metricRegistry);
            webSocket = new TransportMetrics("webSocket", metricRegistry);
            xhrPolling = new TransportMetrics("xhrPolling", metricRegistry);
            xhrSend = new TransportMetrics("xhrSend", metricRegistry);
            xhrStreaming = new TransportMetrics("xhrStreaming", metricRegistry);
        }

        public TransportMetrics getTotal() {
            return total;
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

        public TransportMetrics getMetrics(Class transportClass) {
            if (transportClass == EventSourceTransport.class) {
                return eventSource;
            } else if (transportClass == HtmlFileTransport.class) {
                return htmlFile;
            } else if (transportClass == JsonpPollingTransport.class) {
                return jsonp;
            } else if (transportClass == RawWebSocketTransport.class) {
                return rawWebSocket;
            } else if (transportClass == WebSocketTransport.class) {
                return webSocket;
            } else if (transportClass == XhrPollingTransport.class) {
                return xhrPolling;
            } else if (transportClass == XhrSendTransport.class) {
                return xhrSend;
            } else if (transportClass == XhrStreamingTransport.class) {
                return xhrStreaming;
            } else {
                throw new RuntimeException("Unknown transport " + transportClass.getSimpleName());
            }
        }
    }

}
