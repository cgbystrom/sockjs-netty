package com.cgbystrom.sockjs;

import com.cgbystrom.sockjs.transports.*;
import com.codahale.metrics.MetricRegistry;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.CharsetUtil;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServiceRouter extends SimpleChannelHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ServiceRouter.class);
    private static final Pattern SERVER_SESSION = Pattern.compile("^/([^/.]+)/([^/.]+)/");
    private static final Random random = new Random();
    private enum SessionCreation { CREATE_OR_REUSE, FORCE_REUSE, FORCE_CREATE }

    private final Map<String, Service> services = new LinkedHashMap<String, Service>();
    private IframePage iframe;
    private MetricRegistry metricRegistry = new MetricRegistry();
    private Timer timer = new HashedWheelTimer();

    /**
     *
     * @param clientUrl URL to SockJS JavaScript client. Needed by the iframe to properly load.
     *                  (Hint: SockJS has a CDN, http://cdn.sockjs.org/)
     */
    public ServiceRouter(String clientUrl) {
        this.iframe = new IframePage(clientUrl);
    }

    public synchronized Service registerService(Service service) {
        services.put(service.getUrl(), service);

        if (service.getMetricRegistry() == null) {
            service.setMetricRegistry(metricRegistry);
        }

        if (service.getTimer() == null) {
            service.setTimer(timer);
        }

        return service;
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    public void setMetricRegistry(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest)e.getMessage();
        if (logger.isDebugEnabled())
            logger.debug("URI " + request.getUri());

        for (Service service : services.values()) {
            // Check if there's a service registered with this URL
            if (request.getUri().startsWith(service.getUrl())) {
                handleService(ctx, e, service);
                super.messageReceived(ctx, e);
                return;
            }
        }

        // No match for service found, return 404
        HttpResponse response = new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.NOT_FOUND);
        response.setContent(ChannelBuffers.copiedBuffer("Not found", CharsetUtil.UTF_8));
        writeResponse(e.getChannel(), request, response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        final String connectionClosedMsg = "An existing connection was forcibly closed by the remote host";
        final Throwable t = e.getCause();

        if (t instanceof IOException && t.getMessage().equalsIgnoreCase(connectionClosedMsg)) {
            logger.debug("Unexpected close (may be safe to ignore).");
        } else {
            super.exceptionCaught(ctx, e);
        }
    }

    private void handleService(ChannelHandlerContext ctx, MessageEvent e, Service service) throws Exception {
        HttpRequest request = (HttpRequest)e.getMessage();
        request.setUri(request.getUri().replaceFirst(service.getUrl(), ""));
        QueryStringDecoder qsd = new QueryStringDecoder(request.getUri());
        String path = qsd.getPath();

        HttpResponse response = new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.OK);
        if (path.equals("") || path.equals("/")) {
            response.setHeader(CONTENT_TYPE, BaseTransport.CONTENT_TYPE_PLAIN);
            response.setContent(ChannelBuffers.copiedBuffer("Welcome to SockJS!\n", CharsetUtil.UTF_8));
            writeResponse(e.getChannel(), request, response);
        } else if (path.startsWith("/iframe")) {
            iframe.handle(request, response);
            writeResponse(e.getChannel(), request, response);
        } else if (path.startsWith("/info")) {
            response.setHeader(CONTENT_TYPE, "application/json; charset=UTF-8");
            response.setHeader(CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
            response.setContent(getInfo(service));
            writeResponse(e.getChannel(), request, response);
        } else if (path.startsWith("/websocket")) {
            // Raw web socket
            ctx.getPipeline().addLast("sockjs-websocket", new RawWebSocketTransport(path));
            SessionHandler sessionHandler = service.getOrCreateSession(
                    "rawwebsocket-" + random.nextLong(),
                    service.getMetrics().getRawWebSocket(), true);
            ctx.getPipeline().addLast("sockjs-session-handler", sessionHandler);
        } else {
            if (!handleSession(ctx, e, path, service)) {
                response.setStatus(HttpResponseStatus.NOT_FOUND);
                response.setContent(ChannelBuffers.copiedBuffer("Not found", CharsetUtil.UTF_8));
                writeResponse(e.getChannel(), request, response);
            }
        }
    }

    private boolean handleSession(ChannelHandlerContext ctx, MessageEvent e, String path, Service sm) throws Exception {
        HttpRequest request = (HttpRequest)e.getMessage();
        Matcher m = SERVER_SESSION.matcher(path);

        if (!m.find()) {
            return false;
        }

        String server = m.group(1);
        String sessionId = m.group(2);
        String transport = path.replaceFirst("/" + server + "/" + sessionId, "");
        final ChannelPipeline pipeline = ctx.getPipeline();
        SessionCreation sessionCreation = SessionCreation.CREATE_OR_REUSE;

        TransportMetrics tm;
        if (transport.equals("/xhr_send")) {
            tm = sm.getMetrics().getXhrSend();
            pipeline.addLast("sockjs-xhr-send", new XhrSendTransport(sm.getMetrics(), false));
            sessionCreation = SessionCreation.FORCE_REUSE; // Expect an existing session
        } else if (transport.equals("/jsonp_send")) {
            tm = sm.getMetrics().getXhrSend();
            pipeline.addLast("sockjs-jsonp-send", new XhrSendTransport(sm.getMetrics(), true));
            sessionCreation = SessionCreation.FORCE_REUSE; // Expect an existing session
        } else if (transport.equals("/xhr_streaming")) {
            tm = sm.getMetrics().getXhrStreaming();
            pipeline.addLast("sockjs-xhr-streaming", new XhrStreamingTransport(sm.getMetrics(), sm.getMaxResponseSize()));
        } else if (transport.equals("/xhr")) {
            tm = sm.getMetrics().getXhrPolling();
            pipeline.addLast("sockjs-xhr-polling", new XhrPollingTransport(sm.getMetrics()));
        } else if (transport.equals("/jsonp")) {
            tm = sm.getMetrics().getJsonp();
            pipeline.addLast("sockjs-jsonp-polling", new JsonpPollingTransport(sm.getMetrics()));
        } else if (transport.equals("/htmlfile")) {
            tm = sm.getMetrics().getHtmlFile();
            pipeline.addLast("sockjs-htmlfile-polling", new HtmlFileTransport(sm.getMetrics(), sm.getMaxResponseSize()));
        } else if (transport.equals("/eventsource")) {
            tm = sm.getMetrics().getEventSource();
            pipeline.addLast("sockjs-eventsource", new EventSourceTransport(sm.getMetrics(), sm.getMaxResponseSize()));
        } else if (transport.equals("/websocket")) {
            tm = sm.getMetrics().getWebSocket();
            pipeline.addLast("sockjs-websocket", new WebSocketTransport(sm.getUrl() + path, sm));
            // Websockets should re-create a session every time
            sessionCreation = SessionCreation.FORCE_CREATE;
        } else {
            return false;
        }

        tm.connectionsOpen.inc();
        tm.connectionsOpened.mark();

        SessionHandler sessionHandler = null;
        switch (sessionCreation) {
            case CREATE_OR_REUSE:
                sessionHandler = sm.getOrCreateSession(sessionId, tm, false);
                break;
            case FORCE_REUSE:
                sessionHandler = sm.getSession(sessionId);
                break;
            case FORCE_CREATE:
                sessionHandler = sm.getOrCreateSession(sessionId, tm, true);
                break;
            default:
                throw new Exception("Unknown sessionCreation value: " + sessionCreation);
        }

        pipeline.addLast("sockjs-session-handler", sessionHandler);

        return true;
    }

    /** Handle conditional connection close depending on keep-alive */
    private void writeResponse(Channel channel, HttpRequest request, HttpResponse response) {
        response.setHeader(CONTENT_LENGTH, response.getContent().readableBytes());

        boolean hasKeepAliveHeader = KEEP_ALIVE.equalsIgnoreCase(request.getHeader(CONNECTION));
        if (!request.getProtocolVersion().isKeepAliveDefault() && hasKeepAliveHeader) {
            response.setHeader(CONNECTION, KEEP_ALIVE);
        }

        ChannelFuture wf = channel.write(response);
        if (!HttpHeaders.isKeepAlive(request)) {
            wf.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private ChannelBuffer getInfo(Service metadata) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("{");
        sb.append("\"websocket\": ");
        sb.append(metadata.isWebSocketEnabled());
        sb.append(", ");
        sb.append("\"origins\": [\"*:*\"], ");
        sb.append("\"cookie_needed\": ");
        sb.append(metadata.isCookieNeeded());
        sb.append(", ");
        sb.append("\"entropy\": ");
        sb.append(random.nextInt(Integer.MAX_VALUE) + 1);
        sb.append("}");
        return ChannelBuffers.copiedBuffer(sb.toString(), CharsetUtil.UTF_8);
    }


}
